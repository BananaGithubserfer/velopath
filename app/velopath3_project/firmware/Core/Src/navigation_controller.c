#include "navigation_controller.h"

#include "board_config.h"
#include "compass_lis3mdl.h"
#include "motor_control.h"

#include <math.h>
#include <string.h>

typedef struct {
    bool has_active_guidance;
    nav_direction_t pending_direction;
    float target_heading_deg;
    uint32_t heading_control_deadline_ms;
} navigation_state_t;

static navigation_state_t g_state;

static float normalize_angle(float angle) {
    while (angle <= -180.0f) {
        angle += 360.0f;
    }
    while (angle > 180.0f) {
        angle -= 360.0f;
    }
    return angle;
}

static nav_direction_t resolve_direction_from_error(float error_deg) {
    if (error_deg < 0.0f) {
        return NAV_DIRECTION_LEFT;
    }
    if (error_deg > 0.0f) {
        return NAV_DIRECTION_RIGHT;
    }
    return NAV_DIRECTION_UNKNOWN;
}

static void pulse_direction(nav_direction_t direction, uint32_t duration_ms) {
    if (direction == NAV_DIRECTION_LEFT) {
        motor_control_pulse_left(duration_ms);
    } else if (direction == NAV_DIRECTION_RIGHT) {
        motor_control_pulse_right(duration_ms);
    }
}

static void apply_single_vibro_pattern(nav_direction_t direction, float abs_error_deg, uint32_t now_ms) {
    uint32_t period_ms = 0U;
    uint32_t on_ms = 0U;
    uint32_t phase_ms = 0U;
    uint8_t drive_on = 0U;

    if (abs_error_deg <= 25.0f) {
        period_ms = 900U;
        on_ms = 80U;
    } else if (abs_error_deg <= 45.0f) {
        period_ms = 650U;
        on_ms = 120U;
    } else if (abs_error_deg <= 90.0f) {
        period_ms = 420U;
        on_ms = 170U;
    } else {
        period_ms = 220U;
        on_ms = 220U;
    }

    phase_ms = now_ms % period_ms;
    if (direction == NAV_DIRECTION_RIGHT) {
        /* Right turn: double-pulse signature in one period. */
        uint32_t short_on_ms = on_ms / 2U;
        if (short_on_ms < 30U) {
            short_on_ms = 30U;
        }
        drive_on = (phase_ms < short_on_ms) ||
                   (phase_ms >= (short_on_ms * 2U) && phase_ms < (short_on_ms * 3U));
    } else {
        /* Left turn (or unknown): single pulse signature. */
        drive_on = (phase_ms < on_ms);
    }

    motor_control_set_left_percent(drive_on ? 100U : 0U);
    motor_control_set_right_percent(0U);
}

void navigation_controller_init(void) {
    memset(&g_state, 0, sizeof(g_state));
}

void navigation_controller_stop(void) {
    g_state.has_active_guidance = false;
    g_state.pending_direction = NAV_DIRECTION_UNKNOWN;
    g_state.target_heading_deg = 0.0f;
    g_state.heading_control_deadline_ms = 0U;
    motor_control_stop_all();
}

void navigation_controller_handle_command(const nav_command_t *command) {
    if (command == NULL) {
        return;
    }

    switch (command->type) {
    case NAV_COMMAND_ADVANCE_TURN:
        pulse_direction(command->direction, 300U);
        break;
    case NAV_COMMAND_TURN_NOW:
        g_state.has_active_guidance = true;
        g_state.pending_direction = command->direction;
        g_state.target_heading_deg = (float)command->magnetic_heading_degrees;
        g_state.heading_control_deadline_ms = HAL_GetTick() + 8000U;
        break;
    case NAV_COMMAND_ROUTE_FINISHED:
        g_state.has_active_guidance = false;
        motor_control_pulse_both(1200U);
        break;
    case NAV_COMMAND_NONE:
    default:
        break;
    }
}

void navigation_controller_update(uint32_t now_ms) {
    float current_heading_deg;
    float error_deg;
    float abs_error_deg;
    nav_direction_t active_direction;

    if (!g_state.has_active_guidance) {
        return;
    }
    if (now_ms >= g_state.heading_control_deadline_ms) {
        navigation_controller_stop();
        return;
    }
    if (!compass_lis3mdl_read_heading_degrees(&current_heading_deg)) {
        return;
    }

    error_deg = normalize_angle(g_state.target_heading_deg - current_heading_deg);
    abs_error_deg = fabsf(error_deg);
    active_direction = g_state.pending_direction;
    if (active_direction == NAV_DIRECTION_UNKNOWN) {
        active_direction = resolve_direction_from_error(error_deg);
    }

    if (abs_error_deg <= TURN_HEADING_TOLERANCE_DEG) {
        motor_control_stop_all();
        return;
    }

    /*
     * Single contact vibromotor is currently wired to the LEFT output pin.
     * We encode turn direction by pulse shape and urgency by pulse rate.
     */
    apply_single_vibro_pattern(active_direction, abs_error_deg, now_ms);
}

uint8_t navigation_controller_is_guiding(void) {
    return g_state.has_active_guidance ? 1U : 0U;
}

nav_direction_t navigation_controller_get_pending_direction(void) {
    return g_state.pending_direction;
}

float navigation_controller_get_target_heading_degrees(void) {
    return g_state.target_heading_deg;
}
