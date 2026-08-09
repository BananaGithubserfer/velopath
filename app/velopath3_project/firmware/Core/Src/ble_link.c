#include "ble_link.h"

#include "board_config.h"

#include <string.h>

#define BLE_UART_DEFAULT_BAUD 115200U
#define BLE_BOOT_DELAY_MS 4500U
#define BLE_AT_GUARD_TIME_MS 350U
#define BLE_AT_REPLY_TIMEOUT_MS 320U
#define BLE_AT_CONFIRM_TIMEOUT_MS 3000U
#define BLE_AT_COMMAND_TIMEOUT_MS 1500U
#define BLE_CONFIG_RETRY_COUNT 3U
#define BLE_LINE_IDLE_TIMEOUT_MS 40U
#define BLE_BAUD_AUTOPROBE_INTERVAL_MS 1200U

typedef struct {
    const char *command;
    const char *success_token;
    uint32_t timeout_ms;
    uint16_t post_delay_ms;
} ble_at_command_t;

static const ble_at_command_t g_runtime_config_commands[] = {
    {"AT+NAME=VeloPath\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+UART=115200,8,0,0\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+MODE=S\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+WMODE=0\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+UUID=AA+0003CDD0\r\n", "successful", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+UUID=BB+0003CDD1\r\n", "successful", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+UUID=CC+0003CDD2\r\n", "successful", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+ADP=ON\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+MULTILINK=OFF\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+PAIR=OFF\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+PASSEN=OFF\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+TRENC=OFF\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+ADPTIM=10\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+NOTIFY=ON\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+UARTFT=3\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+PKLEN=200\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U},
    {"AT+AUTOSLEEP=OFF,5\r\n", "OK", BLE_AT_COMMAND_TIMEOUT_MS, 120U}
};

static char g_line_buffer[BLE_LINE_BUFFER_SIZE];
static char g_ready_line[BLE_LINE_BUFFER_SIZE];
static uint16_t g_line_index;
static bool g_line_ready;
static bool g_line_overflowed;
static ble_link_status_t g_status;
static uint32_t g_last_baud_switch_ms;
static const uint32_t g_uart_probe_baud_rates[] = {115200U, 57600U, 38400U, 19200U, 9600U};
static uint8_t g_uart_probe_index = 0U;

#if BLE_LED_DEBUG
static void ble_led_diag_init(void) {
    GPIO_InitTypeDef gpio_init = {0};
    gpio_init.Pin = LEFT_MOTOR_GPIO_PIN | RIGHT_MOTOR_GPIO_PIN;
    gpio_init.Mode = GPIO_MODE_OUTPUT_PP;
    gpio_init.Pull = GPIO_NOPULL;
    gpio_init.Speed = GPIO_SPEED_FREQ_HIGH;
    HAL_GPIO_Init(LEFT_MOTOR_GPIO_PORT, &gpio_init);
}

static void ble_led_diag_set(uint8_t left_on, uint8_t right_on) {
    HAL_GPIO_WritePin(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN,
                      left_on ? LEFT_MOTOR_ACTIVE_STATE : LEFT_MOTOR_INACTIVE_STATE);
    HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN,
                      right_on ? RIGHT_MOTOR_ACTIVE_STATE : RIGHT_MOTOR_INACTIVE_STATE);
}

static void ble_led_diag_pulse(uint8_t left_on, uint8_t right_on, uint16_t on_ms) {
    ble_led_diag_set(left_on, right_on);
    HAL_Delay(on_ms);
    ble_led_diag_set(0U, 0U);
    HAL_Delay(80U);
}
#endif

static void ble_uart_set_baud(uint32_t baud_rate) {
    HAL_UART_DeInit(&BLE_UART_HANDLE);
    BLE_UART_HANDLE.Init.BaudRate = baud_rate;
    if (HAL_UART_Init(&BLE_UART_HANDLE) != HAL_OK) {
        Error_Handler();
    }
    g_status.uart_baud_rate = baud_rate;
}

static void ble_uart_flush(uint32_t timeout_ms) {
    uint8_t byte = 0U;

    while (HAL_UART_Receive(&BLE_UART_HANDLE, &byte, 1U, timeout_ms) == HAL_OK) {
    }
}

static bool ble_uart_send_raw(const char *text) {
    if (text == NULL) {
        return false;
    }

    return HAL_UART_Transmit(&BLE_UART_HANDLE,
                             (uint8_t *)text,
                             (uint16_t)strlen(text),
                             500U) == HAL_OK;
}

static bool ble_uart_wait_for(const char *token, uint32_t timeout_ms) {
    char window[96];
    uint32_t index = 0U;
    uint8_t byte = 0U;
    uint32_t started_at = HAL_GetTick();

    if (token == NULL || token[0] == '\0') {
        return false;
    }

    memset(window, 0, sizeof(window));
    while ((HAL_GetTick() - started_at) < timeout_ms) {
        if (HAL_UART_Receive(&BLE_UART_HANDLE, &byte, 1U, 20U) != HAL_OK) {
            continue;
        }

        if (index < (sizeof(window) - 1U)) {
            window[index++] = (char)byte;
            window[index] = '\0';
        } else {
            memmove(window, window + 1, sizeof(window) - 2U);
            window[sizeof(window) - 2U] = (char)byte;
            window[sizeof(window) - 1U] = '\0';
        }

        if (strstr(window, token) != NULL) {
            return true;
        }
    }

    return false;
}

static bool ble_uart_wait_for_success_or_error(const char *success_token, uint32_t timeout_ms) {
    char window[128];
    uint32_t index = 0U;
    uint8_t byte = 0U;
    uint32_t started_at = HAL_GetTick();

    if (success_token == NULL || success_token[0] == '\0') {
        return false;
    }

    memset(window, 0, sizeof(window));
    while ((HAL_GetTick() - started_at) < timeout_ms) {
        if (HAL_UART_Receive(&BLE_UART_HANDLE, &byte, 1U, 20U) != HAL_OK) {
            continue;
        }

        if (index < (sizeof(window) - 1U)) {
            window[index++] = (char)byte;
            window[index] = '\0';
        } else {
            memmove(window, window + 1, sizeof(window) - 2U);
            window[sizeof(window) - 2U] = (char)byte;
            window[sizeof(window) - 1U] = '\0';
        }

        if (strstr(window, success_token) != NULL) {
            return true;
        }
        if (strcmp(success_token, "OK") != 0 && strstr(window, "OK") != NULL) {
            return true;
        }
        if (strstr(window, "ERR") != NULL) {
            return false;
        }
    }

    return false;
}

static bool ble_link_enter_serial_at_mode(void) {
    ble_uart_flush(5U);

    /* Fast path: some modules are already in AT mode right after boot. */
    if (ble_uart_send_raw("AT\r\n") &&
        ble_uart_wait_for_success_or_error("OK", BLE_AT_REPLY_TIMEOUT_MS)) {
        return true;
    }

    /*
     * Datasheet section 4.1.1:
     * - guard time before sending "+++"
     * - wait for module reply "a"
     * - send final "a" inside the confirmation window
     */
    HAL_Delay(BLE_AT_GUARD_TIME_MS);
    if (ble_uart_send_raw("+++") &&
        ble_uart_wait_for("a", BLE_AT_REPLY_TIMEOUT_MS) &&
        ble_uart_send_raw("a") &&
        ble_uart_wait_for("+OK", BLE_AT_CONFIRM_TIMEOUT_MS)) {
        return true;
    }

    /*
     * The quick-start section also documents the compact "+++a" form.
     * Try it as a fallback because some modules are much more reliable
     * with the condensed sequence.
     */
    ble_uart_flush(5U);
    HAL_Delay(BLE_AT_GUARD_TIME_MS);
    if (!ble_uart_send_raw("+++a")) {
        return false;
    }
    if (ble_uart_wait_for("+OK", BLE_AT_CONFIRM_TIMEOUT_MS)) {
        return true;
    }

    /* Fallback: some firmware accepts +++ directly without the 'a' window. */
    ble_uart_flush(5U);
    HAL_Delay(BLE_AT_GUARD_TIME_MS);
    if (!ble_uart_send_raw("+++")) {
        return false;
    }
    return ble_uart_wait_for("+OK", BLE_AT_CONFIRM_TIMEOUT_MS);
}

static bool ble_link_send_at_command(const ble_at_command_t *command) {
    if (command == NULL || command->command == NULL) {
        return false;
    }

    ble_uart_flush(5U);
    if (!ble_uart_send_raw(command->command)) {
        return false;
    }
    if (!ble_uart_wait_for_success_or_error(command->success_token, command->timeout_ms)) {
        return false;
    }

    if (command->post_delay_ms > 0U) {
        HAL_Delay(command->post_delay_ms);
    }
    return true;
}

static bool ble_link_apply_command_list(const ble_at_command_t *commands, uint32_t count) {
    uint32_t i = 0U;

    for (i = 0U; i < count; ++i) {
        if (!ble_link_send_at_command(&commands[i])) {
            return false;
        }
    }

    return true;
}

static bool ble_link_enter_at_with_baud_probe(void) {
    uint32_t i = 0U;

    for (i = 0U; i < (sizeof(g_uart_probe_baud_rates) / sizeof(g_uart_probe_baud_rates[0])); ++i) {
        ble_uart_set_baud(g_uart_probe_baud_rates[i]);
        HAL_Delay(120U);
        if (ble_link_enter_serial_at_mode()) {
            return true;
        }
    }

    return false;
}

static bool ble_link_apply_wh_ble106_profile(void) {
    uint32_t attempt = 0U;

    ble_uart_set_baud(BLE_UART_DEFAULT_BAUD);
#if BLE_LED_DEBUG
    ble_led_diag_init();
    ble_led_diag_pulse(1U, 1U, 220U);
#endif
    for (attempt = 1U; attempt <= BLE_CONFIG_RETRY_COUNT; ++attempt) {
        g_status.config_attempts = (uint8_t)attempt;
        HAL_Delay(BLE_BOOT_DELAY_MS);
        ble_uart_flush(5U);

#if BLE_LED_DEBUG
        ble_led_diag_set(1U, 1U);
#endif
        if (!ble_link_enter_at_with_baud_probe()) {
#if BLE_LED_DEBUG
            ble_led_diag_pulse(1U, 1U, 180U);
#endif
            continue;
        }

#if BLE_LED_DEBUG
        ble_led_diag_set(1U, 1U);
#endif
        if (!ble_uart_send_raw("AT\r\n") ||
            !ble_uart_wait_for_success_or_error("OK", BLE_AT_COMMAND_TIMEOUT_MS)) {
#if BLE_LED_DEBUG
            ble_led_diag_pulse(1U, 1U, 180U);
#endif
            continue;
        }

        if (!ble_link_apply_command_list(g_runtime_config_commands,
                                         sizeof(g_runtime_config_commands) / sizeof(g_runtime_config_commands[0]))) {
#if BLE_LED_DEBUG
            ble_led_diag_pulse(1U, 1U, 260U);
#endif
            continue;
        }

        if (!ble_uart_send_raw("AT+Z\r\n") ||
            !ble_uart_wait_for_success_or_error("+RST:OK", 4000U)) {
#if BLE_LED_DEBUG
            ble_led_diag_pulse(1U, 1U, 220U);
#endif
            continue;
        }

        HAL_Delay(1200U);
        ble_uart_flush(5U);
#if BLE_LED_DEBUG
        ble_led_diag_pulse(1U, 1U, 320U);
#endif
        return true;
    }

#if BLE_LED_DEBUG
    ble_led_diag_pulse(1U, 1U, 120U);
    ble_led_diag_pulse(1U, 1U, 120U);
    ble_led_diag_pulse(1U, 1U, 120U);
#endif
    return false;
}

static void ble_link_finalize_line(void) {
    if (g_line_index == 0U || g_line_ready) {
        g_line_index = 0U;
        g_line_overflowed = false;
        memset(g_line_buffer, 0, sizeof(g_line_buffer));
        return;
    }

    g_line_buffer[g_line_index] = '\0';
    strncpy(g_ready_line, g_line_buffer, sizeof(g_ready_line) - 1U);
    g_ready_line[sizeof(g_ready_line) - 1U] = '\0';
    g_line_ready = true;
    g_line_index = 0U;
    g_line_overflowed = false;
    memset(g_line_buffer, 0, sizeof(g_line_buffer));
}

void ble_link_init(void) {
    memset(g_line_buffer, 0, sizeof(g_line_buffer));
    memset(g_ready_line, 0, sizeof(g_ready_line));
    memset(&g_status, 0, sizeof(g_status));

    g_status.state = BLE_LINK_STATE_UNINITIALIZED;
    ble_uart_set_baud(BLE_UART_DEFAULT_BAUD);
    g_uart_probe_index = 0U;
    g_last_baud_switch_ms = HAL_GetTick();
    g_status.config_applied = ble_link_apply_wh_ble106_profile();
    g_status.state = g_status.config_applied ? BLE_LINK_STATE_TRANSPARENT : BLE_LINK_STATE_DEGRADED;
    g_status.last_rx_tick_ms = HAL_GetTick();
}

void ble_link_poll_uart(void) {
    uint8_t byte = 0U;
    uint8_t got_any_byte = 0U;

    while (HAL_UART_Receive(&BLE_UART_HANDLE, &byte, 1U, 0U) == HAL_OK) {
        got_any_byte = 1U;
        if (byte == '\0') {
            continue;
        }

        g_status.last_rx_tick_ms = HAL_GetTick();
        if (byte == '\r' || byte == '\n') {
            if (g_line_index > 0U || g_line_overflowed) {
                ble_link_finalize_line();
            }
            continue;
        }

        if (byte == '\b' || byte == 0x7FU) {
            if (g_line_index > 0U) {
                g_line_index--;
                g_line_buffer[g_line_index] = '\0';
            }
            continue;
        }

        if (byte < 0x20U || byte > 0x7EU) {
            continue;
        }

        if (g_line_index < (BLE_LINE_BUFFER_SIZE - 1U)) {
            g_line_buffer[g_line_index++] = (char)byte;
            g_line_buffer[g_line_index] = '\0';
        } else {
            g_line_overflowed = true;
        }
    }

    if (!g_line_ready &&
        g_line_index > 0U &&
        (HAL_GetTick() - g_status.last_rx_tick_ms) >= BLE_LINE_IDLE_TIMEOUT_MS) {
        ble_link_finalize_line();
    }

    if (got_any_byte) {
        g_status.state = BLE_LINK_STATE_TRANSPARENT;
        return;
    }

    if (!g_status.config_applied &&
        !g_line_ready &&
        g_line_index == 0U &&
        (HAL_GetTick() - g_last_baud_switch_ms) >= BLE_BAUD_AUTOPROBE_INTERVAL_MS) {
        ble_link_debug_cycle_baud();
        g_last_baud_switch_ms = HAL_GetTick();
    }
}

bool ble_link_poll_line(char *out_line, uint16_t out_size) {
    if (!g_line_ready || out_line == NULL || out_size == 0U) {
        return false;
    }

    strncpy(out_line, g_ready_line, out_size - 1U);
    out_line[out_size - 1U] = '\0';
    g_line_ready = false;
    memset(g_ready_line, 0, sizeof(g_ready_line));
    return true;
}

bool ble_link_write(const char *text) {
    return ble_uart_send_raw(text);
}

void ble_link_get_status(ble_link_status_t *out_status) {
    if (out_status == NULL) {
        return;
    }

    *out_status = g_status;
}

const char *ble_link_state_name(ble_link_state_t state) {
    switch (state) {
    case BLE_LINK_STATE_TRANSPARENT:
        return "transparent";
    case BLE_LINK_STATE_DEGRADED:
        return "degraded";
    case BLE_LINK_STATE_UNINITIALIZED:
    default:
        return "uninitialized";
    }
}

void ble_link_debug_cycle_baud(void) {
    uint32_t count = sizeof(g_uart_probe_baud_rates) / sizeof(g_uart_probe_baud_rates[0]);
    if (count == 0U) {
        return;
    }

    g_uart_probe_index++;
    if (g_uart_probe_index >= count) {
        g_uart_probe_index = 0U;
    }
    ble_uart_set_baud(g_uart_probe_baud_rates[g_uart_probe_index]);
    ble_uart_flush(2U);
}

uint32_t ble_link_get_uart_baud(void) {
    return g_status.uart_baud_rate;
}
