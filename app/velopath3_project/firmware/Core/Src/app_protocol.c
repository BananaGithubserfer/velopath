#include "app_protocol.h"

#include <stdlib.h>
#include <string.h>

static bool parse_int_field(const char *line, const char *key, int *value) {
    const char *cursor = strstr(line, key);
    if (cursor == NULL) {
        return false;
    }

    cursor = strchr(cursor, ':');
    if (cursor == NULL) {
        return false;
    }

    *value = atoi(cursor + 1);
    return true;
}

static nav_direction_t parse_direction(const char *line) {
    if (strstr(line, "\"direction\":\"left\"") != NULL) {
        return NAV_DIRECTION_LEFT;
    }
    if (strstr(line, "\"direction\":\"right\"") != NULL) {
        return NAV_DIRECTION_RIGHT;
    }
    return NAV_DIRECTION_UNKNOWN;
}

bool app_protocol_parse_line(const char *line, nav_command_t *command) {
    if (line == NULL || command == NULL) {
        return false;
    }

    memset(command, 0, sizeof(*command));

    if (strstr(line, "\"type\":\"advance_turn\"") != NULL) {
        command->type = NAV_COMMAND_ADVANCE_TURN;
        command->direction = parse_direction(line);
        parse_int_field(line, "\"seconds_to_turn\"", &command->seconds_to_turn);
        parse_int_field(line, "\"distance_meters\"", &command->distance_meters);
        return true;
    }

    if (strstr(line, "\"type\":\"turn_now\"") != NULL) {
        command->type = NAV_COMMAND_TURN_NOW;
        command->direction = parse_direction(line);
        parse_int_field(line, "\"magnetic_heading_degrees\"", &command->magnetic_heading_degrees);
        return true;
    }

    if (strstr(line, "\"type\":\"route_finished\"") != NULL) {
        command->type = NAV_COMMAND_ROUTE_FINISHED;
        return true;
    }

    return false;
}
