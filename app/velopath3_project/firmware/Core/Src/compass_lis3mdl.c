#include "compass_lis3mdl.h"

#include "board_config.h"

#include <math.h>
#include <stddef.h>

#define LIS3MDL_I2C_ADDR_LOW         (0x1CU << 1)
#define LIS3MDL_I2C_ADDR_HIGH        (0x1EU << 1)
#define LIS3MDL_REG_WHO_AM_I         0x0FU
#define LIS3MDL_REG_CTRL1            0x20U
#define LIS3MDL_REG_CTRL2            0x21U
#define LIS3MDL_REG_CTRL3            0x22U
#define LIS3MDL_REG_CTRL4            0x23U
#define LIS3MDL_REG_OUT_X_L          0x28U
#define LIS3MDL_WHO_AM_I_VALUE       0x3DU

static bool g_compass_ready;
static float g_heading_zero_deg;
static uint8_t g_compass_i2c_addr = LIS3MDL_I2C_ADDR_LOW;

static float normalize_heading(float heading) {
    while (heading < 0.0f) {
        heading += 360.0f;
    }
    while (heading >= 360.0f) {
        heading -= 360.0f;
    }
    return heading;
}

static bool read_reg_at_addr(uint8_t address, uint8_t reg, uint8_t *data, uint16_t size) {
    return HAL_I2C_Mem_Read(&COMPASS_I2C_HANDLE, address, reg, I2C_MEMADD_SIZE_8BIT, data, size, 100U) == HAL_OK;
}

static bool write_reg_at_addr(uint8_t address, uint8_t reg, uint8_t value) {
    return HAL_I2C_Mem_Write(&COMPASS_I2C_HANDLE, address, reg, I2C_MEMADD_SIZE_8BIT, &value, 1U, 100U) == HAL_OK;
}

static bool read_reg(uint8_t reg, uint8_t *data, uint16_t size) {
    return read_reg_at_addr(g_compass_i2c_addr, reg, data, size);
}

static bool write_reg(uint8_t reg, uint8_t value) {
    return write_reg_at_addr(g_compass_i2c_addr, reg, value);
}

static bool detect_compass_address(uint8_t *out_address, uint8_t *out_who_am_i) {
    static const uint8_t candidates[] = {LIS3MDL_I2C_ADDR_LOW, LIS3MDL_I2C_ADDR_HIGH};
    size_t i;
    uint8_t who_am_i = 0U;

    for (i = 0U; i < (sizeof(candidates) / sizeof(candidates[0])); ++i) {
        if (read_reg_at_addr(candidates[i], LIS3MDL_REG_WHO_AM_I, &who_am_i, 1U) &&
            who_am_i == LIS3MDL_WHO_AM_I_VALUE) {
            if (out_address != NULL) {
                *out_address = candidates[i];
            }
            if (out_who_am_i != NULL) {
                *out_who_am_i = who_am_i;
            }
            return true;
        }
    }

    return false;
}

bool compass_lis3mdl_init(void) {
    uint8_t who_am_i = 0U;

    g_compass_ready = false;
    if (!detect_compass_address(&g_compass_i2c_addr, &who_am_i)) {
        return false;
    }
    if (!write_reg(LIS3MDL_REG_CTRL1, 0x70U)) {
        return false;
    }
    if (!write_reg(LIS3MDL_REG_CTRL2, 0x00U)) {
        return false;
    }
    if (!write_reg(LIS3MDL_REG_CTRL3, 0x00U)) {
        return false;
    }
    if (!write_reg(LIS3MDL_REG_CTRL4, 0x0CU)) {
        return false;
    }

    g_compass_ready = true;
    return true;
}

bool compass_lis3mdl_read_raw(lis3mdl_vector3_t *out) {
    uint8_t raw[6];

    if (out == NULL) {
        return false;
    }
    if (!read_reg(LIS3MDL_REG_OUT_X_L | 0x80U, raw, sizeof(raw))) {
        return false;
    }

    out->x = (float)(int16_t)((raw[1] << 8) | raw[0]);
    out->y = (float)(int16_t)((raw[3] << 8) | raw[2]);
    out->z = (float)(int16_t)((raw[5] << 8) | raw[4]);
    return true;
}

bool compass_lis3mdl_read_heading_degrees(float *heading_deg) {
    lis3mdl_vector3_t raw;
    float heading;

    if (heading_deg == NULL) {
        return false;
    }
    if (!compass_lis3mdl_read_raw(&raw)) {
        return false;
    }

    heading = atan2f(raw.y, raw.x) * (180.0f / 3.14159265f);
    heading = normalize_heading(heading);

    *heading_deg = heading;
    return true;
}

bool compass_lis3mdl_read_relative_heading_degrees(float *heading_deg) {
    float absolute_heading;

    if (heading_deg == NULL) {
        return false;
    }
    if (!compass_lis3mdl_read_heading_degrees(&absolute_heading)) {
        return false;
    }

    *heading_deg = normalize_heading(absolute_heading - g_heading_zero_deg);
    return true;
}

bool compass_lis3mdl_is_ready(void) {
    return g_compass_ready;
}

bool compass_lis3mdl_read_who_am_i(uint8_t *who_am_i) {
    if (who_am_i == NULL) {
        return false;
    }
    if (read_reg(LIS3MDL_REG_WHO_AM_I, who_am_i, 1U)) {
        return true;
    }
    return detect_compass_address(&g_compass_i2c_addr, who_am_i);
}

uint8_t compass_lis3mdl_get_i2c_address(void) {
    return (uint8_t)(g_compass_i2c_addr >> 1);
}

bool compass_lis3mdl_set_heading_zero_to_current(void) {
    float heading_deg = 0.0f;

    if (!compass_lis3mdl_read_heading_degrees(&heading_deg)) {
        return false;
    }
    g_heading_zero_deg = normalize_heading(heading_deg);
    return true;
}

void compass_lis3mdl_clear_heading_zero(void) {
    g_heading_zero_deg = 0.0f;
}

float compass_lis3mdl_get_heading_zero_degrees(void) {
    return g_heading_zero_deg;
}
