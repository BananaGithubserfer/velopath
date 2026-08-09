#ifndef MOTOR_CONTROL_H
#define MOTOR_CONTROL_H

#include <stdint.h>

void motor_control_init(void);
void motor_control_set_left_percent(uint8_t percent);
void motor_control_set_right_percent(uint8_t percent);
void motor_control_stop_all(void);
void motor_control_pulse_left(uint32_t duration_ms);
void motor_control_pulse_right(uint32_t duration_ms);
void motor_control_pulse_both(uint32_t duration_ms);
void motor_control_start_esc_calibration(void);
uint8_t motor_control_esc_is_calibrating(void);
uint8_t motor_control_get_left_percent(void);
uint8_t motor_control_get_right_percent(void);
void motor_control_update(uint32_t now_ms);

#endif
