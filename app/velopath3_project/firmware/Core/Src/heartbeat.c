#include "heartbeat.h"

#include "board_config.h"

static uint8_t g_heartbeat_active;

static void heartbeat_configure_outputs(void) {
    GPIO_InitTypeDef gpio_init = {0};

    gpio_init.Pin = LEFT_MOTOR_GPIO_PIN | RIGHT_MOTOR_GPIO_PIN;
    gpio_init.Mode = GPIO_MODE_OUTPUT_PP;
    gpio_init.Pull = GPIO_NOPULL;
    gpio_init.Speed = GPIO_SPEED_FREQ_HIGH;
    HAL_GPIO_Init(LEFT_MOTOR_GPIO_PORT, &gpio_init);
}

static void heartbeat_write(uint8_t left_on, uint8_t right_on) {
    HAL_GPIO_WritePin(LEFT_MOTOR_GPIO_PORT,
                      LEFT_MOTOR_GPIO_PIN,
                      left_on ? LEFT_MOTOR_ACTIVE_STATE : LEFT_MOTOR_INACTIVE_STATE);
    HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT,
                      RIGHT_MOTOR_GPIO_PIN,
                      right_on ? RIGHT_MOTOR_ACTIVE_STATE : RIGHT_MOTOR_INACTIVE_STATE);
}

void heartbeat_init(void) {
    g_heartbeat_active = 0U;
    heartbeat_configure_outputs();
    heartbeat_write(0U, 0U);
}

void heartbeat_start(void) {
    heartbeat_configure_outputs();
    g_heartbeat_active = 1U;
}

void heartbeat_stop(void) {
    g_heartbeat_active = 0U;
    heartbeat_configure_outputs();
    heartbeat_write(0U, 0U);
}

void heartbeat_update(uint32_t now_ms) {
    uint32_t phase;

    if (g_heartbeat_active == 0U) {
        return;
    }

    phase = (now_ms / 500U) % 4U;
    heartbeat_write(phase == 0U, phase == 2U);
}

uint8_t heartbeat_is_active(void) {
    return g_heartbeat_active;
}
