#ifndef BLE_LINK_H
#define BLE_LINK_H

#include <stdbool.h>
#include <stdint.h>

typedef enum {
    BLE_LINK_STATE_UNINITIALIZED = 0,
    BLE_LINK_STATE_TRANSPARENT,
    BLE_LINK_STATE_DEGRADED
} ble_link_state_t;

typedef struct {
    ble_link_state_t state;
    uint32_t uart_baud_rate;
    uint32_t last_rx_tick_ms;
    uint8_t config_attempts;
    bool config_applied;
} ble_link_status_t;

void ble_link_init(void);
void ble_link_poll_uart(void);
bool ble_link_poll_line(char *out_line, uint16_t out_size);
bool ble_link_write(const char *text);
void ble_link_get_status(ble_link_status_t *out_status);
const char *ble_link_state_name(ble_link_state_t state);
void ble_link_debug_cycle_baud(void);
uint32_t ble_link_get_uart_baud(void);

#endif
