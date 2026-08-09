#ifndef NAVIGATION_CONTROLLER_H
#define NAVIGATION_CONTROLLER_H

#include "app_protocol.h"
#include <stdint.h>

void navigation_controller_init(void);
void navigation_controller_handle_command(const nav_command_t *command);
void navigation_controller_update(uint32_t now_ms);
void navigation_controller_stop(void);
uint8_t navigation_controller_is_guiding(void);
nav_direction_t navigation_controller_get_pending_direction(void);
float navigation_controller_get_target_heading_degrees(void);

#endif
