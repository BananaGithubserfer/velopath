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
#include <string.h>

/* USER CODE END Includes */

/* Private typedef -----------------------------------------------------------*/
/* USER CODE BEGIN PTD */

/* USER CODE END PTD */

/* Private define ------------------------------------------------------------*/
/* USER CODE BEGIN PD */
#define BLE_STATUS_LED_PORT GPIOC
#define BLE_STATUS_LED_PIN  GPIO_PIN_1
#define BLE_DIAG_BEACON_MS  2000U

/* USER CODE END PD */

/* Private macro -------------------------------------------------------------*/
/* USER CODE BEGIN PM */

/* USER CODE END PM */

/* Private variables ---------------------------------------------------------*/

/* USER CODE BEGIN PV */
volatile uint32_t g_ble_state = 0;
volatile uint32_t g_ble_baud = 57600;
volatile uint32_t g_ble_rx_count = 0;
volatile uint32_t g_ble_tx_count = 0;

/* USER CODE END PV */

/* Private function prototypes -----------------------------------------------*/
void SystemClock_Config(void);
/* USER CODE BEGIN PFP */
static HAL_StatusTypeDef BLE_SendRaw(const char *s);
static HAL_StatusTypeDef BLE_SendByte(uint8_t b);
static HAL_StatusTypeDef BLE_SendAT(const char *cmd);
static HAL_StatusTypeDef BLE_ReinitUart(uint32_t baud);
static void BLE_FlushRx(void);
static HAL_StatusTypeDef BLE_WaitByte(uint8_t expected, uint32_t timeout_ms);
static HAL_StatusTypeDef BLE_WaitToken(const char *token, uint32_t timeout_ms);
static HAL_StatusTypeDef BLE_EnterAT(void);
static HAL_StatusTypeDef BLE_TrySimpleAT(void);
static uint32_t BLE_AutodetectBaud(void);
static void BLE_ConfigureWhBle106(void);
static void BLE_StatusLedInit(void);
static void BLE_StatusLedTask(void);
static void BLE_BeaconTask(void);
static void BLE_PingPongTask(void);

/* USER CODE END PFP */

/* Private user code ---------------------------------------------------------*/
/* USER CODE BEGIN 0 */
static HAL_StatusTypeDef BLE_SendRaw(const char *s)
{
  HAL_StatusTypeDef st = HAL_UART_Transmit(&huart2, (uint8_t *)s, (uint16_t)strlen(s), 1000);
  if (st == HAL_OK) g_ble_tx_count += (uint32_t)strlen(s);
  return st;
}

static HAL_StatusTypeDef BLE_SendByte(uint8_t b)
{
  HAL_StatusTypeDef st = HAL_UART_Transmit(&huart2, &b, 1, 100);
  if (st == HAL_OK) g_ble_tx_count++;
  return st;
}

static HAL_StatusTypeDef BLE_SendAT(const char *cmd)
{
  HAL_StatusTypeDef st = BLE_SendRaw(cmd);
  if (st != HAL_OK) return st;
  return BLE_SendRaw("\r\n");
}

static HAL_StatusTypeDef BLE_ReinitUart(uint32_t baud)
{
  huart2.Init.BaudRate = baud;
  if (HAL_UART_Init(&huart2) != HAL_OK) return HAL_ERROR;
  g_ble_baud = baud;
  return HAL_OK;
}

static void BLE_FlushRx(void)
{
  uint8_t b = 0;
  while (HAL_UART_Receive(&huart2, &b, 1, 2) == HAL_OK) { /* discard */ }
}

static HAL_StatusTypeDef BLE_WaitByte(uint8_t expected, uint32_t timeout_ms)
{
  uint32_t start = HAL_GetTick();
  uint8_t b = 0;

  while ((HAL_GetTick() - start) < timeout_ms)
  {
    if (HAL_UART_Receive(&huart2, &b, 1, 10) == HAL_OK)
    {
      g_ble_rx_count++;
      if (b == expected) return HAL_OK;
    }
  }

  return HAL_TIMEOUT;
}

static HAL_StatusTypeDef BLE_WaitToken(const char *token, uint32_t timeout_ms)
{
  uint32_t start = HAL_GetTick();
  uint32_t idx = 0;
  uint8_t b = 0;

  while ((HAL_GetTick() - start) < timeout_ms)
  {
    if (HAL_UART_Receive(&huart2, &b, 1, 10) == HAL_OK)
    {
      g_ble_rx_count++;
      if ((char)b == token[idx])
      {
        idx++;
        if (token[idx] == '\0') return HAL_OK;
      }
      else
      {
        idx = ((char)b == token[0]) ? 1U : 0U;
      }
    }
  }

  return HAL_TIMEOUT;
}

static HAL_StatusTypeDef BLE_EnterAT(void)
{
  if (BLE_SendRaw("+++") != HAL_OK) return HAL_ERROR;
  if (BLE_WaitByte('a', 1000) != HAL_OK) return HAL_TIMEOUT;
  if (BLE_SendRaw("a") != HAL_OK) return HAL_ERROR;
  HAL_Delay(100);
  return HAL_OK;
}

static HAL_StatusTypeDef BLE_TrySimpleAT(void)
{
  BLE_FlushRx();
  if (BLE_SendRaw("AT\r\n") != HAL_OK) return HAL_ERROR;
  if (BLE_WaitToken("OK", 180) == HAL_OK) return HAL_OK;

  BLE_FlushRx();
  if (BLE_SendRaw("AT") != HAL_OK) return HAL_ERROR;
  if (BLE_WaitToken("OK", 180) == HAL_OK) return HAL_OK;

  return HAL_TIMEOUT;
}

static uint32_t BLE_AutodetectBaud(void)
{
  const uint32_t baud_list[] = {57600U, 9600U, 115200U, 38400U, 19200U};
  uint32_t i = 0;

  for (i = 0; i < (sizeof(baud_list) / sizeof(baud_list[0])); i++)
  {
    if (BLE_ReinitUart(baud_list[i]) != HAL_OK) continue;
    if (BLE_TrySimpleAT() == HAL_OK) return baud_list[i];
  }

  /* Roll back to project default to keep data-path test predictable. */
  BLE_ReinitUart(57600U);
  return 0U;
}

static void BLE_ConfigureWhBle106(void)
{
  g_ble_state = 1;
  if (BLE_EnterAT() != HAL_OK) { g_ble_state = 0xE1; return; }
  g_ble_state = 2;
  if (BLE_SendAT("AT+MODE=S") != HAL_OK) { g_ble_state = 0xE4; return; }
  g_ble_state = 5;
  HAL_Delay(800); /* MODE change triggers module reboot */

  if (BLE_EnterAT() != HAL_OK) { g_ble_state = 0xE2; return; }
  g_ble_state = 6;
  if (BLE_SendAT("AT+WMODE=0") != HAL_OK) { g_ble_state = 0xE5; return; }
  g_ble_state = 7;
  HAL_Delay(100);
  if (BLE_SendAT("AT+NAME=STM32_BLE") != HAL_OK) { g_ble_state = 0xE6; return; }
  g_ble_state = 8;
  HAL_Delay(100);
  if (BLE_SendAT("AT+PASSEN=ON") != HAL_OK) { g_ble_state = 0xE7; return; }
  g_ble_state = 9;
  HAL_Delay(100);
  if (BLE_SendAT("AT+PASS=123456") != HAL_OK) { g_ble_state = 0xE8; return; }
  g_ble_state = 10;
  HAL_Delay(100);
  if (BLE_SendAT("AT+UART=57600,8,1,N") != HAL_OK) { g_ble_state = 0xE9; return; }
  g_ble_state = 11;
  HAL_Delay(100);
  if (BLE_SendAT("AT+Z") != HAL_OK) { g_ble_state = 0xEB; return; }
  HAL_Delay(800); /* reboot to apply all params */

  if (BLE_EnterAT() != HAL_OK) { g_ble_state = 0xE3; return; }
  g_ble_state = 12;
  if (BLE_SendAT("AT+ENTM") != HAL_OK) { g_ble_state = 0xEA; return; }
  g_ble_state = 100;
}

static void BLE_StatusLedInit(void)
{
  GPIO_InitTypeDef GPIO_InitStruct = {0};

  __HAL_RCC_GPIOC_CLK_ENABLE();
  GPIO_InitStruct.Pin = BLE_STATUS_LED_PIN;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(BLE_STATUS_LED_PORT, &GPIO_InitStruct);

  HAL_GPIO_WritePin(BLE_STATUS_LED_PORT, BLE_STATUS_LED_PIN, GPIO_PIN_SET);
}

static void BLE_StatusLedTask(void)
{
  static uint32_t last = 0;
  uint32_t now = HAL_GetTick();
  uint32_t period = 500;

  if (g_ble_state == 100)
  {
    period = 1000; /* BLE configured: slow blink */
  }
  else if ((g_ble_state >= 0xE0U) || (g_ble_state > 0U && g_ble_state < 100U))
  {
    period = 200;  /* Config in progress or error: fast blink */
  }
  else
  {
    period = 500; /* heartbeat even before BLE state is known */
  }

  if ((now - last) >= period)
  {
    last = now;
    HAL_GPIO_TogglePin(BLE_STATUS_LED_PORT, BLE_STATUS_LED_PIN);
  }
}

static void BLE_BeaconTask(void)
{
  static uint32_t last = 0;
  uint32_t now = HAL_GetTick();

  if ((now - last) >= BLE_DIAG_BEACON_MS)
  {
    last = now;
    BLE_SendRaw("ble-alive\r\n");
  }
}

static void BLE_PingPongTask(void)
{
  static uint8_t ping_state = 0;
  uint8_t b = 0;
  uint8_t c = 0;

  while (HAL_UART_Receive(&huart2, &b, 1, 0) == HAL_OK)
  {
    g_ble_rx_count++;
    BLE_SendByte(b); /* raw echo to simplify phone-side diagnostics */

    c = b;
    if (c >= 'A' && c <= 'Z') c = (uint8_t)(c + ('a' - 'A'));

    if ((ping_state == 0 && c == 'p') ||
        (ping_state == 1 && c == 'i') ||
        (ping_state == 2 && c == 'n') ||
        (ping_state == 3 && c == 'g'))
    {
      ping_state++;
      if (ping_state == 4)
      {
        BLE_SendRaw("pong\r\n");
        ping_state = 0;
      }
    }
    else
    {
      ping_state = (c == 'p') ? 1 : 0;
    }
  }
}

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
  BLE_StatusLedInit();
  /* Try to detect module baud and keep diagnostics in data mode. */
  if (BLE_AutodetectBaud() != 0U)
  {
    g_ble_state = 120;
  }
  else
  {
    g_ble_state = 0xF0;
  }
  BLE_SendRaw("BLE DIAG READY\r\n");

  /* USER CODE END 2 */

  /* Infinite loop */
  /* USER CODE BEGIN WHILE */
  while (1)
  {
    /* USER CODE END WHILE */

    /* USER CODE BEGIN 3 */
    BLE_PingPongTask();
    BLE_BeaconTask();
    BLE_StatusLedTask();
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

  /** Initializes the RCC Oscillators according to the specified parameters
  * in the RCC_OscInitTypeDef structure.
  */
  RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSI;
  RCC_OscInitStruct.HSEState = RCC_HSE_OFF;
  RCC_OscInitStruct.HSEPredivValue = RCC_HSE_PREDIV_DIV1;
  RCC_OscInitStruct.HSIState = RCC_HSI_ON;
  RCC_OscInitStruct.PLL.PLLState = RCC_PLL_ON;
  RCC_OscInitStruct.PLL.PLLSource = RCC_PLLSOURCE_HSI_DIV2;
  RCC_OscInitStruct.PLL.PLLMUL = RCC_PLL_MUL16;
  if (HAL_RCC_OscConfig(&RCC_OscInitStruct) != HAL_OK)
  {
    Error_Handler();
  }

  /** Initializes the CPU, AHB and APB buses clocks
  */
  RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK|RCC_CLOCKTYPE_SYSCLK
                              |RCC_CLOCKTYPE_PCLK1|RCC_CLOCKTYPE_PCLK2;
  RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_PLLCLK;
  RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
  RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV2;
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
  /* User can add his own implementation to report the HAL error return state */
  GPIO_InitTypeDef GPIO_InitStruct = {0};

  __disable_irq();
  __HAL_RCC_GPIOC_CLK_ENABLE();
  GPIO_InitStruct.Pin = BLE_STATUS_LED_PIN;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(BLE_STATUS_LED_PORT, &GPIO_InitStruct);

  while (1)
  {
    HAL_GPIO_TogglePin(BLE_STATUS_LED_PORT, BLE_STATUS_LED_PIN);
    for (volatile uint32_t i = 0; i < 300000; i++) { __NOP(); }
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
