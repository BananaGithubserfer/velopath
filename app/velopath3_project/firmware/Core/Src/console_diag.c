#include "console_diag.h"

#include "ble_link.h"
#include "board_config.h"
#include "compass_lis3mdl.h"
#include "motor_control.h"
#include "navigation_controller.h"

#include <ctype.h>
#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define COMMAND_PULSE_MS 300U
#define CONSOLE_BUFFER_SIZE 192U
#define VDDA_VREFINT_MV 1200.0f
#define COMPASS_STREAM_DEFAULT_PERIOD_MS 500U

static bool g_power_monitor_ready;
static bool g_compass_stream_enabled;
static uint32_t g_compass_stream_period_ms = COMPASS_STREAM_DEFAULT_PERIOD_MS;
static uint32_t g_compass_stream_next_ms;

static void console_diag_print_fixed2(const char *prefix, float value, const char *suffix) {
    int32_t scaled = (int32_t)lroundf(value * 100.0f);
    int32_t whole = scaled / 100;
    int32_t frac = scaled >= 0 ? (scaled % 100) : -(scaled % 100);
    console_diag_printf("%s%ld.%02ld%s", prefix, (long)whole, (long)frac, suffix);
}

static void power_monitor_init(void) {
    RCC->CFGR &= ~RCC_CFGR_ADCPRE;
    RCC->CFGR |= RCC_CFGR_ADCPRE_DIV6;
    RCC->APB2ENR |= RCC_APB2ENR_ADC1EN;

    ADC1->CR1 = 0U;
    ADC1->CR2 = ADC_CR2_TSVREFE;
    ADC1->SMPR1 &= ~(ADC_SMPR1_SMP17);
    ADC1->SMPR1 |= ADC_SMPR1_SMP17;
    ADC1->SQR1 = 0U;
    ADC1->SQR2 = 0U;
    ADC1->SQR3 = 17U;

    ADC1->CR2 |= ADC_CR2_ADON;
    HAL_Delay(1U);
    ADC1->CR2 |= ADC_CR2_RSTCAL;
    while ((ADC1->CR2 & ADC_CR2_RSTCAL) != 0U) {
    }
    ADC1->CR2 |= ADC_CR2_CAL;
    while ((ADC1->CR2 & ADC_CR2_CAL) != 0U) {
    }

    g_power_monitor_ready = true;
}

static bool power_monitor_read_vdda(float *out_vdda) {
    uint32_t raw = 0U;
    uint32_t sum = 0U;
    uint32_t valid_samples = 0U;
    uint8_t sample = 0U;

    if (!g_power_monitor_ready || out_vdda == NULL) {
        return false;
    }

    for (sample = 0U; sample < 12U; ++sample) {
        ADC1->SR = 0U;
        ADC1->CR2 |= ADC_CR2_ADON;
        while ((ADC1->SR & ADC_SR_EOC) == 0U) {
        }
        raw = ADC1->DR;

        if (sample >= 4U && raw > 0U) {
            sum += raw;
            valid_samples++;
        }
    }

    if (valid_samples == 0U) {
        return false;
    }

    raw = sum / valid_samples;
    *out_vdda = (VDDA_VREFINT_MV * 4095.0f) / (float)raw / 1000.0f;
    return true;
}

static const char *direction_to_string(nav_direction_t direction) {
    switch (direction) {
    case NAV_DIRECTION_LEFT:
        return "left";
    case NAV_DIRECTION_RIGHT:
        return "right";
    case NAV_DIRECTION_UNKNOWN:
    default:
        return "unknown";
    }
}

static void normalize_command(const char *line, char *out, size_t out_size) {
    size_t index = 0U;
    const unsigned char *cursor = (const unsigned char *)line;
    bool previous_was_space = false;

    if (out == NULL || out_size == 0U) {
        return;
    }

    memset(out, 0, out_size);
    if (line == NULL) {
        return;
    }

    while (*cursor != '\0' && isspace(*cursor)) {
        cursor++;
    }

    while (*cursor != '\0' && index < (out_size - 1U)) {
        if (isspace(*cursor)) {
            if (!previous_was_space) {
                out[index++] = ' ';
                previous_was_space = true;
            }
        } else {
            out[index++] = (char)tolower(*cursor);
            previous_was_space = false;
        }
        cursor++;
    }

    while (index > 0U && out[index - 1U] == ' ') {
        index--;
    }
    out[index] = '\0';
}

static void print_help(void) {
    console_diag_printf(
        "commands:\r\n"
        "  help, ping, info, status, ble status, uptime, selftest, reboot\r\n"
        "  nav status, nav stop\r\n"
        "  compass, compass status, compass raw, compass whoami, compass reinit\r\n"
        "  compass zero, compass zero set, compass zero clear\r\n"
        "  compass stream on/off/status, compass stream <period_ms>\r\n"
        "  led left on/off/pulse, led right on/off/pulse, led both on/off/pulse\r\n"
        "  left on/off/pulse, right on/off/pulse, both on/off/pulse, stop\r\n"
        "  esc arm, esc calibrate, esc min, esc max, esc on, esc off, esc 0..100\r\n"
    );
}

static void print_compass_stream_line(void) {
    lis3mdl_vector3_t raw = {0};
    float heading_abs = 0.0f;
    float heading_rel = 0.0f;

    if (!compass_lis3mdl_read_raw(&raw)) {
        console_diag_printf("stream compass raw=read-failed\r\n");
        return;
    }

    if (!compass_lis3mdl_read_heading_degrees(&heading_abs) ||
        !compass_lis3mdl_read_relative_heading_degrees(&heading_rel)) {
        console_diag_printf("stream compass heading=read-failed raw x=%ld y=%ld z=%ld\r\n",
                            (long)lroundf(raw.x),
                            (long)lroundf(raw.y),
                            (long)lroundf(raw.z));
        return;
    }

    console_diag_printf("stream raw x=%ld y=%ld z=%ld",
                        (long)lroundf(raw.x),
                        (long)lroundf(raw.y),
                        (long)lroundf(raw.z));
    console_diag_print_fixed2(" abs=", heading_abs, "");
    console_diag_print_fixed2(" rel=", heading_rel, "");
    console_diag_print_fixed2(" zero=", compass_lis3mdl_get_heading_zero_degrees(), "\r\n");
}

static void print_compass_summary(void) {
    uint8_t who_am_i = 0U;
    lis3mdl_vector3_t raw = {0};
    float heading_abs = 0.0f;
    float heading_rel = 0.0f;
    bool who_ok = compass_lis3mdl_read_who_am_i(&who_am_i);
    bool raw_ok = compass_lis3mdl_read_raw(&raw);
    bool abs_ok = compass_lis3mdl_read_heading_degrees(&heading_abs);
    bool rel_ok = compass_lis3mdl_read_relative_heading_degrees(&heading_rel);

    console_diag_printf(
        "compass: ready=%s addr=0x%02X whoami=%s",
        compass_lis3mdl_is_ready() ? "yes" : "no",
        (unsigned int)compass_lis3mdl_get_i2c_address(),
        who_ok ? "" : "read-failed"
    );
    if (who_ok) {
        console_diag_printf("0x%02X", who_am_i);
    }
    console_diag_printf("\r\n");

    if (raw_ok) {
        console_diag_printf("raw: x=%ld y=%ld z=%ld\r\n",
                            (long)lroundf(raw.x),
                            (long)lroundf(raw.y),
                            (long)lroundf(raw.z));
    } else {
        console_diag_printf("raw: read failed\r\n");
    }

    if (abs_ok && rel_ok) {
        console_diag_print_fixed2("heading: abs=", heading_abs, "");
        console_diag_print_fixed2(" rel=", heading_rel, "");
        console_diag_print_fixed2(" zero=", compass_lis3mdl_get_heading_zero_degrees(), "\r\n");
    } else {
        console_diag_printf("heading: read failed\r\n");
    }
}

static void print_navigation_summary(void) {
    console_diag_printf(
        "nav: guiding=%s direction=%s target=",
        navigation_controller_is_guiding() ? "yes" : "no",
        direction_to_string(navigation_controller_get_pending_direction())
    );
    console_diag_print_fixed2("", navigation_controller_get_target_heading_degrees(), "\r\n");
}

static void print_ble_summary(void) {
    ble_link_status_t status = {0};

    ble_link_get_status(&status);
    console_diag_printf("ble: module=WH-BLE106 state=%s configured=%s baud=%lu attempts=%u\r\n",
                        ble_link_state_name(status.state),
                        status.config_applied ? "yes" : "no",
                        (unsigned long)status.uart_baud_rate,
                        (unsigned int)status.config_attempts);
}

static void print_board_info(void) {
    console_diag_printf(
        "velopath firmware\r\n"
        "build: %s %s\r\n"
        "uart2: WH-BLE106 slave transparent bridge\r\n",
        __DATE__,
        __TIME__
    );
    print_ble_summary();
}

static void print_status(void) {
    float vdda = 0.0f;
    bool vdda_ok = power_monitor_read_vdda(&vdda);

    console_diag_printf(
        "uptime_ms=%lu left=%u%% right=%u%% esc_cal=%u",
        (unsigned long)HAL_GetTick(),
        (unsigned int)motor_control_get_left_percent(),
        (unsigned int)motor_control_get_right_percent(),
        (unsigned int)motor_control_esc_is_calibrating()
    );
    if (vdda_ok) {
        console_diag_print_fixed2(" vdda=", vdda, "V\r\n");
    } else {
        console_diag_printf(" vdda=read-failed\r\n");
    }
    print_navigation_summary();
    print_ble_summary();
    print_compass_summary();
}

static void run_selftest(void) {
    uint8_t who_am_i = 0U;
    lis3mdl_vector3_t raw = {0};
    float heading_abs = 0.0f;

    console_diag_printf("selftest:\r\n");
    console_diag_printf("  motors default -> stop\r\n");
    motor_control_stop_all();
    navigation_controller_stop();

    if (compass_lis3mdl_read_who_am_i(&who_am_i)) {
        console_diag_printf("  compass whoami: ok (0x%02X)\r\n", who_am_i);
    } else {
        console_diag_printf("  compass whoami: fail\r\n");
    }

    if (compass_lis3mdl_init()) {
        console_diag_printf("  compass init: ok\r\n");
    } else {
        console_diag_printf("  compass init: fail\r\n");
    }

    if (compass_lis3mdl_read_raw(&raw)) {
        console_diag_printf("  compass raw: ok (%ld, %ld, %ld)\r\n",
                            (long)lroundf(raw.x),
                            (long)lroundf(raw.y),
                            (long)lroundf(raw.z));
    } else {
        console_diag_printf("  compass raw: fail\r\n");
    }

    if (compass_lis3mdl_read_heading_degrees(&heading_abs)) {
        console_diag_print_fixed2("  compass heading: ok (", heading_abs, " deg)\r\n");
    } else {
        console_diag_printf("  compass heading: fail\r\n");
    }

    if (power_monitor_read_vdda(&heading_abs)) {
        console_diag_print_fixed2("  vdda: ok (", heading_abs, " V)\r\n");
    } else {
        console_diag_printf("  vdda: fail\r\n");
    }

    console_diag_printf("  nav: %s\r\n", navigation_controller_is_guiding() ? "active" : "idle");
}

void console_diag_init(void) {
    power_monitor_init();
    g_compass_stream_enabled = false;
    g_compass_stream_period_ms = COMPASS_STREAM_DEFAULT_PERIOD_MS;
    g_compass_stream_next_ms = 0U;
    console_diag_printf("\r\nvelopath console ready\r\n");
    print_board_info();
    print_status();
    print_help();
    console_diag_print_prompt();
}

void console_diag_update(uint32_t now_ms) {
    if (!g_compass_stream_enabled) {
        return;
    }

    if ((int32_t)(now_ms - g_compass_stream_next_ms) < 0) {
        return;
    }

    print_compass_stream_line();
    g_compass_stream_next_ms = now_ms + g_compass_stream_period_ms;
}

void console_diag_printf(const char *format, ...) {
    char buffer[CONSOLE_BUFFER_SIZE];
    va_list args;
    int length;

    va_start(args, format);
    length = vsnprintf(buffer, sizeof(buffer), format, args);
    va_end(args);

    if (length <= 0) {
        return;
    }
    if (length >= (int)sizeof(buffer)) {
        length = (int)sizeof(buffer) - 1;
    }

    if (!ble_link_write(buffer)) {
        HAL_UART_Transmit(&BLE_UART_HANDLE, (uint8_t *)buffer, (uint16_t)length, 300U);
    }
}

void console_diag_print_prompt(void) {
    console_diag_printf("vp> ");
}

bool console_diag_try_handle(const char *line) {
    char command[BLE_LINE_BUFFER_SIZE];
    char *endptr = NULL;
    long esc_percent = 0;
    float heading_deg = 0.0f;
    lis3mdl_vector3_t raw = {0};
    uint8_t who_am_i = 0U;

    if (line == NULL || line[0] == '\0') {
        return false;
    }

    normalize_command(line, command, sizeof(command));
    if (command[0] == '\0') {
        console_diag_print_prompt();
        return true;
    }

    if (strcmp(command, "help") == 0) {
        print_help();
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "ping") == 0) {
        console_diag_printf("pong\r\n");
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "info") == 0 || strcmp(command, "version") == 0) {
        print_board_info();
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "status") == 0 || strcmp(command, "board") == 0) {
        print_status();
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "ble status") == 0 || strcmp(command, "link status") == 0) {
        print_ble_summary();
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "uptime") == 0) {
        console_diag_printf("uptime_ms=%lu\r\n", (unsigned long)HAL_GetTick());
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "voltage") == 0 || strcmp(command, "battery") == 0 || strcmp(command, "vdda") == 0) {
        float vdda = 0.0f;
        if (power_monitor_read_vdda(&vdda)) {
            console_diag_print_fixed2("vdda=", vdda, "V\r\n");
        } else {
            console_diag_printf("vdda read failed\r\n");
        }
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "selftest") == 0) {
        run_selftest();
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "reboot") == 0) {
        console_diag_printf("rebooting...\r\n");
        HAL_Delay(50U);
        NVIC_SystemReset();
        return true;
    }

    if (strcmp(command, "nav status") == 0) {
        print_navigation_summary();
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "nav stop") == 0) {
        navigation_controller_stop();
        console_diag_printf("nav=stopped\r\n");
        console_diag_print_prompt();
        return true;
    }

    if (strcmp(command, "compass") == 0 || strcmp(command, "compass heading") == 0) {
        if (compass_lis3mdl_read_heading_degrees(&heading_deg)) {
            float relative_deg = 0.0f;
            if (compass_lis3mdl_read_relative_heading_degrees(&relative_deg)) {
                console_diag_print_fixed2("heading abs=", heading_deg, "");
                console_diag_print_fixed2(" rel=", relative_deg, "");
                console_diag_print_fixed2(" zero=", compass_lis3mdl_get_heading_zero_degrees(), "\r\n");
            } else {
                console_diag_print_fixed2("heading abs=", heading_deg, " rel=read-failed\r\n");
            }
        } else {
            console_diag_printf("compass read failed\r\n");
        }
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "compass status") == 0) {
        print_compass_summary();
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "compass raw") == 0) {
        if (compass_lis3mdl_read_raw(&raw)) {
            console_diag_printf("raw x=%ld y=%ld z=%ld\r\n",
                                (long)lroundf(raw.x),
                                (long)lroundf(raw.y),
                                (long)lroundf(raw.z));
        } else {
            console_diag_printf("compass raw read failed\r\n");
        }
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "compass whoami") == 0) {
        if (compass_lis3mdl_read_who_am_i(&who_am_i)) {
            console_diag_printf("whoami=0x%02X\r\n", who_am_i);
        } else {
            console_diag_printf("compass whoami read failed\r\n");
        }
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "compass reinit") == 0) {
        if (compass_lis3mdl_init()) {
            console_diag_printf("compass=reinitialized\r\n");
        } else {
            console_diag_printf("compass reinit failed\r\n");
        }
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "compass zero") == 0) {
        console_diag_print_fixed2("compass_zero=", compass_lis3mdl_get_heading_zero_degrees(), "\r\n");
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "compass zero set") == 0) {
        if (compass_lis3mdl_set_heading_zero_to_current()) {
            console_diag_print_fixed2("compass_zero=", compass_lis3mdl_get_heading_zero_degrees(), "\r\n");
        } else {
            console_diag_printf("compass zero set failed\r\n");
        }
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "compass zero clear") == 0) {
        compass_lis3mdl_clear_heading_zero();
        console_diag_printf("compass_zero=0.00\r\n");
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "compass stream on") == 0) {
        g_compass_stream_enabled = true;
        g_compass_stream_next_ms = HAL_GetTick();
        console_diag_printf("compass_stream=on period_ms=%lu\r\n", (unsigned long)g_compass_stream_period_ms);
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "compass stream off") == 0) {
        g_compass_stream_enabled = false;
        console_diag_printf("compass_stream=off\r\n");
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "compass stream status") == 0) {
        console_diag_printf("compass_stream=%s period_ms=%lu\r\n",
                            g_compass_stream_enabled ? "on" : "off",
                            (unsigned long)g_compass_stream_period_ms);
        console_diag_print_prompt();
        return true;
    }
    if (strncmp(command, "compass stream ", 15) == 0) {
        long period_ms = strtol(command + 15, &endptr, 10);
        if (endptr != command + 15 && *endptr == '\0' && period_ms >= 100L && period_ms <= 10000L) {
            g_compass_stream_period_ms = (uint32_t)period_ms;
            if (g_compass_stream_enabled) {
                g_compass_stream_next_ms = HAL_GetTick();
            }
            console_diag_printf("compass_stream_period_ms=%ld\r\n", period_ms);
            console_diag_print_prompt();
            return true;
        }
    }

    if (strcmp(command, "led left on") == 0 || strcmp(command, "left on") == 0) {
        motor_control_set_left_percent(100U);
        console_diag_printf("left=on\r\n");
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "led left off") == 0 || strcmp(command, "left off") == 0) {
        motor_control_set_left_percent(0U);
        console_diag_printf("left=off\r\n");
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "led left pulse") == 0 || strcmp(command, "left pulse") == 0) {
        motor_control_pulse_left(COMMAND_PULSE_MS);
        console_diag_printf("left=pulse\r\n");
        console_diag_print_prompt();
        return true;
    }

    if (strcmp(command, "led right on") == 0 || strcmp(command, "right on") == 0) {
        motor_control_set_right_percent(100U);
        console_diag_printf("right=on\r\n");
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "led right off") == 0 || strcmp(command, "right off") == 0) {
        motor_control_set_right_percent(0U);
        console_diag_printf("right=off\r\n");
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "led right pulse") == 0 || strcmp(command, "right pulse") == 0) {
        motor_control_pulse_right(COMMAND_PULSE_MS);
        console_diag_printf("right=pulse\r\n");
        console_diag_print_prompt();
        return true;
    }

    if (strcmp(command, "led both on") == 0 || strcmp(command, "both on") == 0) {
        motor_control_set_left_percent(100U);
        motor_control_set_right_percent(100U);
        console_diag_printf("both=on\r\n");
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "led both off") == 0 || strcmp(command, "both off") == 0 || strcmp(command, "stop") == 0) {
        navigation_controller_stop();
        motor_control_stop_all();
        console_diag_printf("outputs=off\r\n");
        console_diag_print_prompt();
        return true;
    }
    if (strcmp(command, "led both pulse") == 0 || strcmp(command, "both pulse") == 0) {
        motor_control_pulse_both(COMMAND_PULSE_MS);
        console_diag_printf("both=pulse\r\n");
        console_diag_print_prompt();
        return true;
    }

      if (strcmp(command, "esc on") == 0 || strcmp(command, "esc max") == 0) {
#if ESC_ENABLED
          motor_control_set_right_percent(100U);
          console_diag_printf("esc=max\r\n");
          console_diag_print_prompt();
#else
          console_diag_printf("esc=disabled\r\n");
          console_diag_print_prompt();
#endif
          return true;
      }
      if (strcmp(command, "esc off") == 0 || strcmp(command, "esc min") == 0 || strcmp(command, "esc arm") == 0) {
#if ESC_ENABLED
          motor_control_set_right_percent(0U);
          console_diag_printf("esc=min\r\n");
          console_diag_print_prompt();
#else
          console_diag_printf("esc=disabled\r\n");
          console_diag_print_prompt();
#endif
          return true;
      }
      if (strcmp(command, "esc calibrate") == 0) {
#if ESC_ENABLED
          motor_control_start_esc_calibration();
          console_diag_printf("esc=calibrating high 5s then low 5s\r\n");
          console_diag_print_prompt();
#else
          console_diag_printf("esc=disabled\r\n");
          console_diag_print_prompt();
#endif
          return true;
      }
    if (strncmp(command, "esc ", 4) == 0) {
#if !ESC_ENABLED
        console_diag_printf("esc=disabled\r\n");
        console_diag_print_prompt();
        return true;
#else
        esc_percent = strtol(command + 4, &endptr, 10);
        if (endptr != command + 4 && *endptr == '\0' && esc_percent >= 0 && esc_percent <= 100) {
            motor_control_set_right_percent((uint8_t)esc_percent);
            console_diag_printf("esc=%ld%%\r\n", esc_percent);
            console_diag_print_prompt();
            return true;
        }
#endif
    }

    return false;
}
