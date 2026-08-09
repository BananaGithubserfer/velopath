#ifndef CONSOLE_DIAG_H
#define CONSOLE_DIAG_H

#include <stdbool.h>
#include <stdint.h>

void console_diag_init(void);
void console_diag_update(uint32_t now_ms);
bool console_diag_try_handle(const char *line);
void console_diag_printf(const char *format, ...);
void console_diag_print_prompt(void);

#endif
