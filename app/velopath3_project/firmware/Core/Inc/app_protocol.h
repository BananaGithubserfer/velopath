#ifndef APP_PROTOCOL_H
#define APP_PROTOCOL_H

#include <stdbool.h>

typedef enum {
    NAV_COMMAND_NONE = 0,
    NAV_COMMAND_ADVANCE_TURN,
    NAV_COMMAND_TURN_NOW,
    NAV_COMMAND_ROUTE_FINISHED
} nav_command_type_t;

typedef enum {
    NAV_DIRECTION_UNKNOWN = 0,
    NAV_DIRECTION_LEFT,
    NAV_DIRECTION_RIGHT
} nav_direction_t;

typedef struct {
    nav_command_type_t type;
    nav_direction_t direction;
    int seconds_to_turn;
    int distance_meters;
    int magnetic_heading_degrees;
} nav_command_t;

bool app_protocol_parse_line(const char *line, nav_command_t *command);

#endif
