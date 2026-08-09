#ifndef COMPASS_LIS3MDL_H
#define COMPASS_LIS3MDL_H

#include <stdbool.h>
#include <stdint.h>

typedef struct {
    float x;
    float y;
    float z;
} lis3mdl_vector3_t;

bool compass_lis3mdl_init(void);
bool compass_lis3mdl_read_raw(lis3mdl_vector3_t *out);
bool compass_lis3mdl_read_heading_degrees(float *heading_deg);
bool compass_lis3mdl_read_relative_heading_degrees(float *heading_deg);
bool compass_lis3mdl_is_ready(void);
bool compass_lis3mdl_read_who_am_i(uint8_t *who_am_i);
uint8_t compass_lis3mdl_get_i2c_address(void);
bool compass_lis3mdl_set_heading_zero_to_current(void);
void compass_lis3mdl_clear_heading_zero(void);
float compass_lis3mdl_get_heading_zero_degrees(void);

#endif
