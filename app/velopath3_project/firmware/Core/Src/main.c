/* USER CODE BEGIN Header */
/**
  ******************************************************************************
  * @file           : main.c
  * @brief          : Main program body
  ******************************************************************************
  * @attention
  *
  * Copyright (c) 2026 STMicroelectronics.
  * All rights reserved.
  *
  * This software is licensed under terms that can be found in the LICENSE file
  * in the root directory of this software component.
  * If no LICENSE file comes with this software, it is provided AS-IS.
  *
  ******************************************************************************
  */
/* USER CODE END Header */
/* Includes ------------------------------------------------------------------*/
#include "main.h"
#include "i2c.h"
#include "usart.h"
#include "gpio.h"

/* Private includes ----------------------------------------------------------*/
/* USER CODE BEGIN Includes */
#include "app_protocol.h"
#include "ble_link.h"
#include "board_config.h"
#include "console_diag.h"
#include "compass_lis3mdl.h"
#include "heartbeat.h"
#include "motor_control.h"
#include "navigation_controller.h"

#include <stdio.h>
#include <string.h>
/* USER CODE END Includes */

/* Private typedef -----------------------------------------------------------*/
/* USER CODE BEGIN PTD */

/* USER CODE END PTD */

/* Private define ------------------------------------------------------------*/
/* USER CODE BEGIN PD */

/* USER CODE END PD */

/* Private macro -------------------------------------------------------------*/
/* USER CODE BEGIN PM */

/* USER CODE END PM */

/* Private variables ---------------------------------------------------------*/

/* USER CODE BEGIN PV */
static char ble_line[BLE_LINE_BUFFER_SIZE];
static nav_command_t nav_command;
/* USER CODE END PV */

/* Private function prototypes -----------------------------------------------*/
void SystemClock_Config(void);
/* USER CODE BEGIN PFP */
#if BLE_LED_DEBUG
static void ble_tx_debug_update(uint32_t now_ms);
static void ble_baud_probe_update(uint32_t now_ms);
#endif
#if UART_TX_PROBE
static void uart_tx_probe_update(uint32_t now_ms);
#endif
#if PIN_TEST_MODE
static void run_pin_test_mode(void);
#endif
/* USER CODE END PFP */

/* Private user code ---------------------------------------------------------*/
/* USER CODE BEGIN 0 */
#if PIN_TEST_MODE
static void run_pin_test_mode(void) {
  GPIO_InitTypeDef gpio_init = {0};

  __HAL_RCC_GPIOA_CLK_ENABLE();

  /* PA2 (USART2_TX): force as plain GPIO output for multimeter UART test. */
  gpio_init.Pin = GPIO_PIN_2;
  gpio_init.Mode = GPIO_MODE_OUTPUT_PP;
  gpio_init.Pull = GPIO_NOPULL;
  gpio_init.Speed = GPIO_SPEED_FREQ_HIGH;
  HAL_GPIO_Init(GPIOA, &gpio_init);

  /* PA3 (USART2_RX): input with pull-up as a stable reference. */
  gpio_init.Pin = GPIO_PIN_3;
  gpio_init.Mode = GPIO_MODE_INPUT;
  gpio_init.Pull = GPIO_PULLUP;
  HAL_GPIO_Init(GPIOA, &gpio_init);

  gpio_init.Pin = LEFT_MOTOR_GPIO_PIN | RIGHT_MOTOR_GPIO_PIN;
  gpio_init.Mode = GPIO_MODE_OUTPUT_PP;
  gpio_init.Pull = GPIO_NOPULL;
  gpio_init.Speed = GPIO_SPEED_FREQ_HIGH;
  HAL_GPIO_Init(LEFT_MOTOR_GPIO_PORT, &gpio_init);

  HAL_GPIO_WritePin(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN, LEFT_MOTOR_INACTIVE_STATE);
  HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, RIGHT_MOTOR_INACTIVE_STATE);

  while (1) {
    /* Phase A: PA2 forced LOW (0V) for 4s. */
    HAL_GPIO_WritePin(GPIOA, GPIO_PIN_2, GPIO_PIN_RESET);
    HAL_GPIO_WritePin(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN, LEFT_MOTOR_ACTIVE_STATE);
    HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, RIGHT_MOTOR_INACTIVE_STATE);
    HAL_Delay(4000U);

    /* Phase B: PA2 forced HIGH (3.3V) for 4s. */
    HAL_GPIO_WritePin(GPIOA, GPIO_PIN_2, GPIO_PIN_SET);
    HAL_GPIO_WritePin(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN, LEFT_MOTOR_INACTIVE_STATE);
    HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, RIGHT_MOTOR_ACTIVE_STATE);
    HAL_Delay(4000U);
  }
}
#endif

#if BLE_LED_DEBUG
static void ble_tx_debug_update(uint32_t now_ms) {
  static uint32_t last_ms = 0U;

  if ((now_ms - last_ms) < 1000U) {
    return;
  }
  last_ms = now_ms;
  ble_link_write("DBG tick\r\n");
}

static void ble_baud_probe_update(uint32_t now_ms) {
  static uint32_t last_switch_ms = 0U;
  static uint32_t last_announce_ms = 0U;
  ble_link_status_t ble_status;
  char line[48];

  ble_link_get_status(&ble_status);
  if ((now_ms - ble_status.last_rx_tick_ms) < 1500U) {
    return;
  }

  if ((now_ms - last_switch_ms) >= 2500U) {
    ble_link_debug_cycle_baud();
    last_switch_ms = now_ms;
    last_announce_ms = 0U;
  }

  if ((now_ms - last_announce_ms) >= 700U) {
    (void)snprintf(line, sizeof(line), "DBG baud=%lu\r\n", (unsigned long)ble_link_get_uart_baud());
    ble_link_write(line);
    last_announce_ms = now_ms;
  }
}
#endif

#if UART_TX_PROBE
static void uart_tx_probe_update(uint32_t now_ms) {
  static uint32_t last_ms = 0U;
  if ((now_ms - last_ms) < 1000U) {
    return;
  }
  last_ms = now_ms;
  ble_link_write("TX_MARK\r\n");
}
#endif

/* USER CODE END 0 */

/**
  * @brief  The application entry point.
  * @retval int
  */
int main(void)
{
  /* USER CODE BEGIN 1 */

  /* USER CODE END 1 */

  /* MCU Configuration--------------------------------------------------------*/

  /* Reset of all peripherals, Initializes the Flash interface and the Systick. */
  HAL_Init();

  /* USER CODE BEGIN Init */

  /* USER CODE END Init */

  /* Configure the system clock */
  SystemClock_Config();

  /* USER CODE BEGIN SysInit */

  /* USER CODE END SysInit */

  /* Initialize all configured peripherals */
  MX_GPIO_Init();
  MX_I2C2_Init();
  MX_USART2_UART_Init();
  /* USER CODE BEGIN 2 */
#if SMOKE_GREEN_BLINK_ONLY
  {
    GPIO_InitTypeDef gpio_init = {0};
    gpio_init.Pin = LEFT_MOTOR_GPIO_PIN | RIGHT_MOTOR_GPIO_PIN;
    gpio_init.Mode = GPIO_MODE_OUTPUT_PP;
    gpio_init.Pull = GPIO_NOPULL;
    gpio_init.Speed = GPIO_SPEED_FREQ_HIGH;
    HAL_GPIO_Init(RIGHT_MOTOR_GPIO_PORT, &gpio_init);

    HAL_GPIO_WritePin(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN, LEFT_MOTOR_INACTIVE_STATE);
    HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, RIGHT_MOTOR_INACTIVE_STATE);

    while (1) {
      /* LED test through the two former motor channels. */
      HAL_GPIO_WritePin(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN, LEFT_MOTOR_ACTIVE_STATE);
      HAL_Delay(300U);
      HAL_GPIO_WritePin(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN, LEFT_MOTOR_INACTIVE_STATE);
      HAL_Delay(300U);

      HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, RIGHT_MOTOR_ACTIVE_STATE);
      HAL_Delay(300U);
      HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, RIGHT_MOTOR_INACTIVE_STATE);
      HAL_Delay(300U);

      HAL_GPIO_WritePin(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN, LEFT_MOTOR_ACTIVE_STATE);
      HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, RIGHT_MOTOR_ACTIVE_STATE);
      HAL_Delay(500U);
      HAL_GPIO_WritePin(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN, LEFT_MOTOR_INACTIVE_STATE);
      HAL_GPIO_WritePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN, RIGHT_MOTOR_INACTIVE_STATE);
      HAL_Delay(700U);
    }
  }
#endif
#if PIN_TEST_MODE
  run_pin_test_mode();
#endif
  motor_control_init();
  heartbeat_init();
  navigation_controller_init();
  compass_lis3mdl_init();
  ble_link_init();
  console_diag_init();
  /* USER CODE END 2 */

  /* Infinite loop */
  /* USER CODE BEGIN WHILE */
  while (1)
  {
    /* USER CODE END WHILE */

    /* USER CODE BEGIN 3 */
    ble_link_poll_uart();

    if (ble_link_poll_line(ble_line, sizeof(ble_line))) {
      if (console_diag_try_handle(ble_line)) {
        /* handled by console */
      } else if (app_protocol_parse_line(ble_line, &nav_command)) {
        navigation_controller_handle_command(&nav_command);
      } else {
        console_diag_printf("unknown command: %s\r\n", ble_line);
        console_diag_print_prompt();
      }
      memset(ble_line, 0, sizeof(ble_line));
    }

    console_diag_update(HAL_GetTick());
    navigation_controller_update(HAL_GetTick());
    motor_control_update(HAL_GetTick());
    heartbeat_update(HAL_GetTick());
#if BLE_LED_DEBUG
    ble_tx_debug_update(HAL_GetTick());
    ble_baud_probe_update(HAL_GetTick());
#endif
#if UART_TX_PROBE
    uart_tx_probe_update(HAL_GetTick());
#endif
  }
  /* USER CODE END 3 */
}

/**
  * @brief System Clock Configuration
  * @retval None
  */
void SystemClock_Config(void)
{
  RCC_OscInitTypeDef RCC_OscInitStruct = {0};
  RCC_ClkInitTypeDef RCC_ClkInitStruct = {0};

  /* HSI-only clock tree: do not use external crystal at all. */
  RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSI;
  RCC_OscInitStruct.HSIState = RCC_HSI_ON;
  RCC_OscInitStruct.HSICalibrationValue = RCC_HSICALIBRATION_DEFAULT;
  RCC_OscInitStruct.HSEState = RCC_HSE_OFF;
  RCC_OscInitStruct.PLL.PLLState = RCC_PLL_ON;
  RCC_OscInitStruct.PLL.PLLSource = RCC_PLLSOURCE_HSI_DIV2;
  RCC_OscInitStruct.PLL.PLLMUL = RCC_PLL_MUL16; /* 8MHz/2 * 16 = 64MHz */
  if (HAL_RCC_OscConfig(&RCC_OscInitStruct) != HAL_OK)
  {
    Error_Handler();
  }

  RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK|RCC_CLOCKTYPE_SYSCLK
                              |RCC_CLOCKTYPE_PCLK1|RCC_CLOCKTYPE_PCLK2;
  RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_PLLCLK;
  RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
  RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV2; /* <=36MHz on APB1 */
  RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV1;
  if (HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_2) != HAL_OK)
  {
    Error_Handler();
  }
}

/* USER CODE BEGIN 4 */

/* USER CODE END 4 */

/**
  * @brief  This function is executed in case of error occurrence.
  * @retval None
  */
void Error_Handler(void)
{
  /* USER CODE BEGIN Error_Handler_Debug */
  GPIO_InitTypeDef gpio_init = {0};
  volatile uint32_t wait = 0U;

  __disable_irq();
  __HAL_RCC_GPIOC_CLK_ENABLE();
  gpio_init.Pin = LEFT_MOTOR_GPIO_PIN | RIGHT_MOTOR_GPIO_PIN;
  gpio_init.Mode = GPIO_MODE_OUTPUT_PP;
  gpio_init.Pull = GPIO_NOPULL;
  gpio_init.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(LEFT_MOTOR_GPIO_PORT, &gpio_init);

  while (1)
  {
    HAL_GPIO_TogglePin(LEFT_MOTOR_GPIO_PORT, LEFT_MOTOR_GPIO_PIN);
    HAL_GPIO_TogglePin(RIGHT_MOTOR_GPIO_PORT, RIGHT_MOTOR_GPIO_PIN);
    for (wait = 0U; wait < 250000U; ++wait) {
      __NOP();
    }
  }
  /* USER CODE END Error_Handler_Debug */
}

#ifdef USE_FULL_ASSERT
/**
  * @brief  Reports the name of the source file and the source line number
  *         where the assert_param error has occurred.
  * @param  file: pointer to the source file name
  * @param  line: assert_param error line source number
  * @retval None
  */
void assert_failed(uint8_t *file, uint32_t line)
{
  /* USER CODE BEGIN 6 */
  /* User can add his own implementation to report the file name and line number,
     ex: printf("Wrong parameters value: file %s on line %d\r\n", file, line) */
  /* USER CODE END 6 */
}
#endif /* USE_FULL_ASSERT */
