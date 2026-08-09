#include "motor_control.h"

#include "board_config.h"

#define ESC_FRAME_PERIOD_MS 20U
#define ESC_MIN_PULSE_US 1000U
#define ESC_MAX_PULSE_US 2000U
#define ESC_ARM_TIME_MS 5000U
#define ESC_CAL_HIGH_TIME_MS 5000U
#define ESC_CAL_LOW_TIME_MS 5000U

typedef enum {
    ESC_MODE_NORMAL = 0,
    ESC_MODE_CAL_HIGH,
    ESC_MODE_CAL_LOW,
} esc_mode_t;

static uint32_t g_left_deadline_ms;
static uint32_t g_right_deadline_ms;
static uint8_t g_left_percent;
static uint8_t g_right_percent;
static uint32_t g_esc_next_frame_ms;
static uint32_t g_cpu_hz;
static uint32_t g_esc_arm_deadline_ms;
static esc_mode_t g_esc_mode;
static uint32_t g_esc_mode_deadline_ms;

static void configure_pin_mode(GPIO_TypeDef *port, uint16_t pin, uint32_t mode) {
    GPIO_InitTypeDef gpio_init = {0};
    gpio_init.Pin = pin;
    gpio_init.Mode = mode;
    gpio_init.Pull = GPIO_NOPULL;
    gpio_init.Speed = GPIO_SPEED_FREQ_LOW;
    HAL_GPIO_Init(port, &gpio_init);
}

static void delay_us(uint32_t microseconds) {
    uint32_t start_cycles = DWT->CYCCNT;
    uint32_t wait_cycles = (g_cpu_hz / 1000000U) * microseconds;
    while ((DWT->CYCCNT - start_cycles) < wait_cycles) {
    }
}

static void init_cycle_counter(void) {
    CoreDebug->DEMCR |= CoreDebug_DEMCR_TRCENA_Msk;
    DWT->CYCCNT = 0U;
    DWT->CTRL |= DWT_CTRL_CYCCNTENA_Msk;
    g_cpu_hz = HAL_RCC_GetHCLKFreq();
}

static void set_channel_idle(GPIO_TypeDef *port, uint16_t pin, GPIO_PinState inactive_state) {
    configure_pin_mode(port, pin, GPIO_MODE_OUTPUT_PP);
    HAL_GPIO_WritePin(port, pin, inactive_state);
}

static void set_channel_output(GPIO_TypeDef *port,
                               uint16_t pin,
                               GPIO_PinState active_state,
                               GPIO_PinState inactive_state,
                               uint8_t on) {
    configure_pin_mode(port, pin, GPIO_MODE_OUTPUT_PP);
    HAL_GPIO_WritePin(port, pin, on ? active_state : inactive_state);
}

static uint32_t percent_to_esc_pulse_us(uint8_t percent) {
    if (percent >= 100U) {
        return ESC_MAX_PULSE_US;
    }
    return ESC_MIN_PULSE_US + (((uint32_t)percent * (ESC_MAX_PULSE_US - ESC_MIN_PULSE_US)) / 100U);
}

static void esc_write_frame(uint8_t percent) {
    uint32_t pulse_us = percent_to_esc_pulse_us(percent);

    configure_pin_mode(ESC_GPIO_PORT, ESC_GPIO_PIN, GPIO_MODE_OUTPUT_PP);
    HAL_GPIO_WritePin(ESC_GPIO_PORT, ESC_GPIO_PIN, ESC_ACTIVE_STATE);
    delay_us(pulse_us);
    HAL_GPIO_WritePin(ESC_GPIO_PORT, ESC_GPIO_PIN, ESC_INACTIVE_STATE);
}

static void apply_motor_state(GPIO_TypeDef *port, uint16_t pin, uint8_t percent) {
    GPIO_PinState active_state = GPIO_PIN_SET;
    GPIO_PinState inactive_state = GPIO_PIN_RESET;

    if (port == LEFT_MOTOR_GPIO_PORT && pin == LEFT_MOTOR_GPIO_PIN) {
        g_left_percent = percent;
        if (percent > 0U) {
            configure_pin_mode(port, pin, GPIO_MODE_OUTPUT_PP);
            HAL_GPIO_WritePin(port, pin, LEFT_MOTOR_ACTIVE_STATE);
        } else {
            configure_pin_mode(port, pin, GPIO_MODE_OUTPUT_PP);
            HAL_GPIO_WritePin(port, pin, LEFT_MOTOR_INACTIVE_STATE);
        }
        return;
    } else if (port == RIGHT_MOTOR_GPIO_PORT && pin == RIGHT_MOTOR_GPIO_PIN) {
        g_right_percent = percent;
        if (percent > 0U) {
            configure_pin_mode(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, GPIO_MODE_OUTPUT_PP);
            HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, RIGHT_MOTOR_ACTIVE_STATE);
        } else {
            configure_pin_mode(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, GPIO_MODE_OUTPUT_PP);
            HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, RIGHT_MOTOR_INACTIVE_STATE);
        }
        return;
    }

    HAL_GPIO_WritePin(port, pin, percent > 0U ? active_state : inactive_state);
}

void motor_control_init(void) {
    init_cycle_counter();
    g_left_deadline_ms = 0U;
    g_right_deadline_ms = 0U;
    g_left_percent = 0U;
    g_right_percent = 0U;
    g_esc_next_frame_ms = HAL_GetTick();
    g_esc_arm_deadline_ms = 0U;
    g_esc_mode = ESC_MODE_NORMAL;
    g_esc_mode_deadline_ms = 0U;
    motor_control_stop_all();
}

void motor_control_set_left_percent(uint8_t percent) {
    apply_motor_state(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN, percent);
}

void motor_control_set_right_percent(uint8_t percent) {
    apply_motor_state(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, percent);
}

void motor_control_stop_all(void) {
    motor_control_set_left_percent(0U);
    motor_control_set_right_percent(0U);
}

void motor_control_pulse_left(uint32_t duration_ms) {
    motor_control_set_left_percent(100U);
    g_left_deadline_ms = HAL_GetTick() + duration_ms;
}

void motor_control_pulse_right(uint32_t duration_ms) {
    motor_control_set_right_percent(100U);
    g_right_deadline_ms = HAL_GetTick() + duration_ms;
}

void motor_control_pulse_both(uint32_t duration_ms) {
    motor_control_pulse_left(duration_ms);
    motor_control_pulse_right(duration_ms);
}

  void motor_control_start_esc_calibration(void) {
#if !ESC_ENABLED
      return;
#else
      g_right_percent = 0U;
      g_esc_mode = ESC_MODE_CAL_HIGH;
      g_esc_mode_deadline_ms = HAL_GetTick() + ESC_CAL_HIGH_TIME_MS;
#endif
  }

uint8_t motor_control_esc_is_calibrating(void) {
    return g_esc_mode != ESC_MODE_NORMAL ? 1U : 0U;
}

uint8_t motor_control_get_left_percent(void) {
    return g_left_percent;
}

uint8_t motor_control_get_right_percent(void) {
    return g_right_percent;
}

  void motor_control_update(uint32_t now_ms) {
      uint8_t esc_percent = g_right_percent;
      uint8_t outputs_idle = 0U;

      if (g_left_deadline_ms != 0U && now_ms >= g_left_deadline_ms) {
          motor_control_set_left_percent(0U);
          g_left_deadline_ms = 0U;
      }
      if (g_right_deadline_ms != 0U && now_ms >= g_right_deadline_ms) {
          motor_control_set_right_percent(0U);
          g_right_deadline_ms = 0U;
      }

#if ESC_ENABLED
      if (g_esc_mode == ESC_MODE_CAL_HIGH && (int32_t)(now_ms - g_esc_mode_deadline_ms) >= 0) {
          g_esc_mode = ESC_MODE_CAL_LOW;
          g_esc_mode_deadline_ms = now_ms + ESC_CAL_LOW_TIME_MS;
      } else if (g_esc_mode == ESC_MODE_CAL_LOW && (int32_t)(now_ms - g_esc_mode_deadline_ms) >= 0) {
          g_esc_mode = ESC_MODE_NORMAL;
          g_esc_mode_deadline_ms = 0U;
          g_esc_arm_deadline_ms = now_ms + ESC_ARM_TIME_MS;
      }

      if (g_esc_mode == ESC_MODE_CAL_HIGH) {
          esc_percent = 100U;
      } else if (g_esc_mode == ESC_MODE_CAL_LOW) {
          esc_percent = 0U;
      } else if ((int32_t)(g_esc_arm_deadline_ms - now_ms) > 0) {
          esc_percent = 0U;
      }
#else
      (void)esc_percent;
#endif

      outputs_idle = (g_left_percent == 0U &&
                      g_right_percent == 0U &&
                      g_left_deadline_ms == 0U &&
                      g_right_deadline_ms == 0U &&
#if ESC_ENABLED
                      g_esc_mode == ESC_MODE_NORMAL &&
                      g_esc_arm_deadline_ms == 0U) ? 1U : 0U;
#else
                      1U) ? 1U : 0U;
#endif

    if (outputs_idle) {
#if BLE_LED_DEBUG
        /*
         * In BLE LED debug mode, keep pins in their current output state so
         * heartbeat/diagnostic patterns stay clearly visible.
         */
        return;
#else
        set_channel_idle(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN, LEFT_MOTOR_INACTIVE_STATE);
        set_channel_idle(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, RIGHT_MOTOR_INACTIVE_STATE);
        return;
#endif
    }

#if ESC_ENABLED
      if ((int32_t)(now_ms - g_esc_next_frame_ms) >= 0) {
          esc_write_frame(esc_percent);
          do {
              g_esc_next_frame_ms += ESC_FRAME_PERIOD_MS;
          } while ((int32_t)(now_ms - g_esc_next_frame_ms) >= 0);
      }
#endif
  }
