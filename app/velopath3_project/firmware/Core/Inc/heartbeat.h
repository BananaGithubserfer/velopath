#ifndef HEARTBEAT_H
#define HEARTBEAT_H

#include <stdint.h>

void heartbeat_init(void);
void heartbeat_start(void);
void heartbeat_stop(void);
void heartbeat_update(uint32_t now_ms);
uint8_t heartbeat_is_active(void);

#endif
