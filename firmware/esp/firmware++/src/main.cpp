#include <Arduino.h>
#include <Wire.h>
#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <ESP32Servo.h>

// ===== Pins =====
static constexpr int ESC_PIN = 4;      // ESC signal pin
static constexpr int I2C_SDA_PIN = 6;  // MPU-6050 SDA
static constexpr int I2C_SCL_PIN = 7;  // MPU-6050 SCL

// ===== I2C / MPU =====
static constexpr uint8_t MPU_ADDR_68 = 0x68;
static constexpr uint8_t MPU_ADDR_69 = 0x69;
static constexpr uint8_t IMU_READ_RETRIES = 3;
static constexpr uint8_t IMU_FAIL_LIMIT = 20;
static constexpr uint32_t IMU_RECOVER_INTERVAL_MS = 1000;

// ===== Control law: heading error -> ESC power =====
static constexpr float DEAD_BAND_DEG = 3.0f;
static constexpr float FULL_THROTTLE_ERROR_DEG = 360.0f;
static constexpr float MIN_ACTIVE_POWER_PERCENT = 0.0f;

// ===== ESC signal =====
static constexpr uint16_t ESC_MIN_US = 1000;
static constexpr uint16_t ESC_MAX_US = 2000;
static constexpr uint16_t ESC_ARM_HOLD_MS = 3000;
static constexpr uint16_t ESC_RATE_HZ = 50;

// ===== BLE =====
static constexpr const char* DEVICE_NAME = "ESP32C3-ESC-NAV";
static constexpr const char* SERVICE_UUID = "7f7b1000-6a66-4a7e-9a4a-57f4f5a22a01";
static constexpr const char* COMMAND_UUID = "7f7b1001-6a66-4a7e-9a4a-57f4f5a22a01";
static constexpr const char* STATUS_UUID = "7f7b1002-6a66-4a7e-9a4a-57f4f5a22a01";

enum class CommandSource {
  Serial,
  Ble,
};

Servo esc;
BLECharacteristic* statusCharacteristic = nullptr;

bool bleConnected = false;

bool imuReady = false;
uint8_t imuAddr = MPU_ADDR_68;
uint8_t imuWhoAmI = 0xFF;
float gyroBiasZ = 0.0f;
float headingDeg = 0.0f;  // 0..360 relative heading from boot/reset
uint32_t imuLastUs = 0;
uint8_t imuConsecutiveReadFails = 0;
uint32_t imuLastRecoverMs = 0;

bool targetActive = false;
float targetHeadingDeg = 0.0f;
float lastErrorDeg = 0.0f;
bool manualPowerMode = false;
float escPowerPercent = 0.0f;
uint16_t escPulseUs = ESC_MIN_US;
bool escArmed = false;

String serialLine;
bool streamEnabled = false;
uint32_t lastStreamMs = 0;

String lastBleRxText;
String lastBleRxHex;

float wrap360(float angle) {
  while (angle >= 360.0f) {
    angle -= 360.0f;
  }
  while (angle < 0.0f) {
    angle += 360.0f;
  }
  return angle;
}

float clockwiseDeltaDeg(float current, float target) {
  float e = target - current;
  while (e >= 360.0f) {
    e -= 360.0f;
  }
  while (e < 0.0f) {
    e += 360.0f;
  }
  return e;
}

String toHexString(const std::string& data) {
  if (data.empty()) {
    return String("");
  }

  String hex;
  hex.reserve(data.size() * 3);
  for (size_t i = 0; i < data.size(); ++i) {
    char tmp[4];
    snprintf(tmp, sizeof(tmp), "%02X", static_cast<uint8_t>(data[i]));
    hex += tmp;
    if (i + 1 < data.size()) {
      hex += " ";
    }
  }
  return hex;
}

String toPrintableString(const std::string& data) {
  String out;
  out.reserve(data.size());
  for (size_t i = 0; i < data.size(); ++i) {
    const uint8_t c = static_cast<uint8_t>(data[i]);
    if (c >= 32 && c <= 126) {
      out += static_cast<char>(c);
    } else {
      out += '.';
    }
  }
  return out;
}

String bytesToCommand(const std::string& data) {
  String cmd;
  cmd.reserve(data.size());

  for (size_t i = 0; i < data.size(); ++i) {
    const uint8_t c = static_cast<uint8_t>(data[i]);
    if (c == 0 || c == '\r' || c == '\n') {
      continue;
    }
    if (c >= 32 && c <= 126) {
      cmd += static_cast<char>(c);
    }
  }

  cmd.trim();
  return cmd;
}

void notifyBleStatus(const String& msg) {
  if (!statusCharacteristic) {
    return;
  }
  statusCharacteristic->setValue(msg.c_str());
  statusCharacteristic->notify();
  Serial.printf("BLE_TX: %s\r\n", msg.c_str());
}

void respond(CommandSource source, const String& msg) {
  if (source == CommandSource::Ble) {
    notifyBleStatus(msg);
  } else {
    Serial.println(msg);
  }
}

bool mpuWriteReg(uint8_t reg, uint8_t value) {
  Wire.beginTransmission(imuAddr);
  Wire.write(reg);
  Wire.write(value);
  return Wire.endTransmission() == 0;
}

bool mpuReadBytes(uint8_t reg, uint8_t* dst, size_t len) {
  for (uint8_t attempt = 0; attempt < IMU_READ_RETRIES; ++attempt) {
    Wire.beginTransmission(imuAddr);
    Wire.write(reg);
    if (Wire.endTransmission(true) != 0) {
      delay(1);
      continue;
    }

    const int readLen = Wire.requestFrom(static_cast<int>(imuAddr), static_cast<int>(len), static_cast<int>(true));
    if (readLen != static_cast<int>(len)) {
      delay(1);
      continue;
    }

    for (size_t i = 0; i < len; ++i) {
      dst[i] = Wire.read();
    }
    return true;
  }
  return false;
}

bool readGyroZDps(float& gzDps) {
  uint8_t raw[6];
  if (!mpuReadBytes(0x43, raw, sizeof(raw))) {  // GYRO_XOUT_H..GYRO_ZOUT_L
    return false;
  }

  const int16_t gzRaw = static_cast<int16_t>((raw[4] << 8) | raw[5]);
  gzDps = static_cast<float>(gzRaw) / 131.0f - gyroBiasZ;
  return true;
}

bool probeMpuAddress(uint8_t addr) {
  const uint8_t oldAddr = imuAddr;
  imuAddr = addr;

  Wire.beginTransmission(imuAddr);
  if (Wire.endTransmission() != 0) {
    imuAddr = oldAddr;
    return false;
  }

  if (!mpuWriteReg(0x6B, 0x00)) {  // wake
    imuAddr = oldAddr;
    return false;
  }
  delay(20);

  uint8_t who = 0;
  if (!mpuReadBytes(0x75, &who, 1)) {
    imuAddr = oldAddr;
    return false;
  }

  if (who == 0x00 || who == 0xFF) {
    imuAddr = oldAddr;
    return false;
  }

  imuWhoAmI = who;
  return true;
}

bool initMpu() {
  delay(60);
  if (!probeMpuAddress(MPU_ADDR_68) && !probeMpuAddress(MPU_ADDR_69)) {
    return false;
  }

  const bool ok1 = mpuWriteReg(0x19, 0x07);  // sample divider
  const bool ok2 = mpuWriteReg(0x1A, 0x03);  // DLPF
  const bool ok3 = mpuWriteReg(0x1B, 0x00);  // gyro +/-250 dps
  const bool ok4 = mpuWriteReg(0x1C, 0x00);  // accel +/-2g

  float testGz = 0.0f;
  const bool okRead = readGyroZDps(testGz);
  return ok1 && ok2 && ok3 && ok4 && okRead;
}

void calibrateGyroBias() {
  const int samples = 300;
  float sum = 0.0f;
  int good = 0;

  for (int i = 0; i < samples; ++i) {
    uint8_t raw[6];
    if (mpuReadBytes(0x43, raw, sizeof(raw))) {
      const int16_t gzRaw = static_cast<int16_t>((raw[4] << 8) | raw[5]);
      sum += static_cast<float>(gzRaw) / 131.0f;
      ++good;
    }
    delay(3);
  }

  if (good > 0) {
    gyroBiasZ = sum / static_cast<float>(good);
  } else {
    gyroBiasZ = 0.0f;
  }
}

bool tryInitImu(bool withCalibration) {
  imuReady = initMpu();
  if (!imuReady) {
    return false;
  }

  imuConsecutiveReadFails = 0;
  imuLastUs = 0;
  if (withCalibration) {
    Serial.println("IMU: keep board still for calibration...");
    calibrateGyroBias();
    Serial.printf("IMU: gyro bias z=%.3f dps\r\n", gyroBiasZ);
  }
  return true;
}

String scanI2CBus() {
  String found;
  for (uint8_t addr = 1; addr < 127; ++addr) {
    Wire.beginTransmission(addr);
    if (Wire.endTransmission() == 0) {
      if (found.length() > 0) {
        found += ",";
      }
      char buf[8];
      snprintf(buf, sizeof(buf), "0x%02X", addr);
      found += buf;
    }
  }
  if (found.length() == 0) {
    return String("none");
  }
  return found;
}

void resetHeading() {
  headingDeg = 0.0f;
}

uint16_t powerToPulseUs(float percent) {
  const float p = constrain(percent, 0.0f, 100.0f);
  const float span = static_cast<float>(ESC_MAX_US - ESC_MIN_US);
  const float pulse = static_cast<float>(ESC_MIN_US) + (p / 100.0f) * span;
  return static_cast<uint16_t>(pulse + 0.5f);
}

void setEscPower(float percent) {
  escPowerPercent = constrain(percent, 0.0f, 100.0f);
  escPulseUs = powerToPulseUs(escPowerPercent);
  esc.writeMicroseconds(escPulseUs);
}

String turnHint() {
  if (lastErrorDeg <= DEAD_BAND_DEG) {
    return String("ok");
  }
  return String("cw");
}

String buildStatusLine() {
  const char* mode = manualPowerMode ? "manual" : (targetActive ? "auto" : "idle");
  char buf[320];
  snprintf(buf,
           sizeof(buf),
           "status:imu=%s,addr=0x%02X,whoami=0x%02X,heading=%.1f,target=%s,error=%.1f,turn=%s,mode=%s,power=%.1f,pulse=%u,armed=%s",
           imuReady ? "ok" : "na",
           imuAddr,
           imuWhoAmI,
           headingDeg,
           targetActive ? String(targetHeadingDeg, 1).c_str() : "none",
           lastErrorDeg,
           turnHint().c_str(),
           mode,
           escPowerPercent,
           escPulseUs,
           escArmed ? "yes" : "no");
  return String(buf);
}

void printSerialHelp() {
  Serial.println("Commands:");
  Serial.println("  help");
  Serial.println("  status");
  Serial.println("  heading");
  Serial.println("  heading reset");
  Serial.println("  target <0..360>   (or: t <0..360>)");
  Serial.println("  power <0..100>    (or: p <0..100>, manual mode)");
  Serial.println("  auto              (return to heading mode)");
  Serial.println("  arm");
  Serial.println("  stop");
  Serial.println("  stream on");
  Serial.println("  stream off");
  Serial.println("  imu status");
  Serial.println("  imu reinit");
  Serial.println("  i2c scan");
}

bool parseUnsignedFloatInRange(const String& raw, float minValue, float maxValue, float& out) {
  String s = raw;
  s.trim();
  if (s.length() == 0) {
    return false;
  }

  bool hasDot = false;
  for (size_t i = 0; i < s.length(); ++i) {
    const char c = s[i];
    if (c == '.') {
      if (hasDot) {
        return false;
      }
      hasDot = true;
      continue;
    }
    if (!isDigit(c)) {
      return false;
    }
  }

  const float v = s.toFloat();
  if (v < minValue || v > maxValue) {
    return false;
  }
  out = v;
  return true;
}

void setTargetHeading(float angleDeg) {
  targetHeadingDeg = wrap360(angleDeg);
  targetActive = true;
  manualPowerMode = false;
}

void setManualPower(float powerPercent) {
  manualPowerMode = true;
  targetActive = false;
  lastErrorDeg = 0.0f;
  setEscPower(powerPercent);
}

void armEsc(CommandSource source) {
  respond(source, "ARM: hold 1000us for 3s...");
  setEscPower(0.0f);
  targetActive = false;
  manualPowerMode = false;
  lastErrorDeg = 0.0f;

  const uint32_t t0 = millis();
  while (millis() - t0 < ESC_ARM_HOLD_MS) {
    delay(20);
  }

  escArmed = true;
  respond(source, "ok:arm");
}

bool executeCommand(const String& rawCmd, CommandSource source) {
  String original = rawCmd;
  original.trim();
  String cmd = original;
  cmd.toLowerCase();

  if (cmd.length() == 0) {
    return true;
  }

  if (cmd == "help") {
    if (source == CommandSource::Ble) {
      respond(source, "help: status|target N|t N|power N|p N|auto|arm|stop|heading|heading reset|imu status|imu reinit|i2c scan");
    } else {
      printSerialHelp();
    }
    return true;
  }

  if (cmd == "status") {
    respond(source, buildStatusLine());
    return true;
  }

  if (cmd == "heading") {
    char buf[40];
    snprintf(buf, sizeof(buf), "heading:%.1f", headingDeg);
    respond(source, String(buf));
    return true;
  }

  if (cmd == "heading reset" || cmd == "reset") {
    resetHeading();
    respond(source, "ok:heading_reset");
    return true;
  }

  if (cmd == "auto") {
    manualPowerMode = false;
    if (!targetActive) {
      respond(source, "ok:auto_idle");
    } else {
      respond(source, String("ok:auto target=") + String(targetHeadingDeg, 1));
    }
    return true;
  }

  if (cmd == "arm") {
    armEsc(source);
    return true;
  }

  if (cmd == "stop") {
    targetActive = false;
    manualPowerMode = false;
    lastErrorDeg = 0.0f;
    setEscPower(0.0f);
    respond(source, "ok:stop");
    return true;
  }

  if (cmd == "stream on") {
    streamEnabled = true;
    respond(source, "ok:stream_on");
    return true;
  }

  if (cmd == "stream off") {
    streamEnabled = false;
    respond(source, "ok:stream_off");
    return true;
  }

  if (cmd == "imu status") {
    respond(source, buildStatusLine());
    return true;
  }

  if (cmd == "i2c scan") {
    respond(source, String("i2c:") + scanI2CBus());
    return true;
  }

  if (cmd == "imu reinit") {
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    Wire.setClock(50000);
    Wire.setTimeOut(30);
    if (tryInitImu(true)) {
      respond(source, String("ok:imu_reinit addr=0x") + String(imuAddr, HEX));
    } else {
      imuReady = false;
      respond(source, String("err:imu_reinit i2c=") + scanI2CBus());
    }
    return true;
  }

  if (cmd.startsWith("target ") || cmd.startsWith("t ")) {
    const int sep = cmd.startsWith("target ") ? 7 : 2;
    const String value = original.substring(sep);
    float angle = 0.0f;
    if (!parseUnsignedFloatInRange(value, 0.0f, 360.0f, angle)) {
      respond(source, "err:target");
      return false;
    }
    setTargetHeading(angle);
    respond(source, String("ok:target=") + String(targetHeadingDeg, 1));
    return true;
  }

  if (cmd.startsWith("power ") || cmd.startsWith("p ")) {
    const int sep = cmd.startsWith("power ") ? 6 : 2;
    const String value = original.substring(sep);
    float power = 0.0f;
    if (!parseUnsignedFloatInRange(value, 0.0f, 100.0f, power)) {
      respond(source, "err:power");
      return false;
    }
    setManualPower(power);
    respond(source, String("ok:power=") + String(escPowerPercent, 1));
    return true;
  }

  // Numeric command alone -> target angle (0..360)
  float angle = 0.0f;
  if (parseUnsignedFloatInRange(cmd, 0.0f, 360.0f, angle)) {
    setTargetHeading(angle);
    respond(source, String("ok:target=") + String(targetHeadingDeg, 1));
    return true;
  }

  respond(source, "err:bad_cmd");
  return false;
}

void handleSerial() {
  while (Serial.available() > 0) {
    const char c = static_cast<char>(Serial.read());
    if (c == '\r' || c == '\n') {
      if (serialLine.length() > 0) {
        executeCommand(serialLine, CommandSource::Serial);
        serialLine = "";
      }
      continue;
    }

    if (serialLine.length() < 120) {
      serialLine += c;
    } else {
      serialLine = "";
      Serial.println("ERR: line too long");
    }
  }
}

void updateImu() {
  if (!imuReady) {
    const uint32_t nowMs = millis();
    if (nowMs - imuLastRecoverMs >= IMU_RECOVER_INTERVAL_MS) {
      imuLastRecoverMs = nowMs;
      if (tryInitImu(false)) {
        Serial.printf("IMU: auto-reinit OK addr=0x%02X whoami=0x%02X\r\n", imuAddr, imuWhoAmI);
      }
    }
    return;
  }

  float gz = 0.0f;
  if (!readGyroZDps(gz)) {
    if (imuConsecutiveReadFails < 255) {
      ++imuConsecutiveReadFails;
    }
    const uint32_t nowMs = millis();
    if (imuConsecutiveReadFails >= IMU_FAIL_LIMIT && nowMs - imuLastRecoverMs >= IMU_RECOVER_INTERVAL_MS) {
      imuLastRecoverMs = nowMs;
      Serial.println("IMU: read errors, recovering...");
      Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
      Wire.setClock(50000);
      Wire.setTimeOut(30);
      if (tryInitImu(false)) {
        Serial.printf("IMU: recovery OK addr=0x%02X whoami=0x%02X\r\n", imuAddr, imuWhoAmI);
      } else {
        imuReady = false;
        Serial.println("IMU: recovery failed");
      }
    }
    return;
  }

  imuConsecutiveReadFails = 0;

  const uint32_t nowUs = micros();
  if (imuLastUs == 0) {
    imuLastUs = nowUs;
    return;
  }

  const float dt = static_cast<float>(nowUs - imuLastUs) / 1000000.0f;
  imuLastUs = nowUs;

  headingDeg = wrap360(headingDeg + gz * dt);
}

void updateEscControl() {
  if (!escArmed) {
    setEscPower(0.0f);
    return;
  }

  if (manualPowerMode) {
    return;
  }

  if (!imuReady || !targetActive) {
    lastErrorDeg = 0.0f;
    setEscPower(0.0f);
    return;
  }

  const float cwError = clockwiseDeltaDeg(headingDeg, targetHeadingDeg);
  lastErrorDeg = cwError;

  if (cwError <= DEAD_BAND_DEG) {
    setEscPower(0.0f);
    return;
  }

  float t = (cwError - DEAD_BAND_DEG) / (FULL_THROTTLE_ERROR_DEG - DEAD_BAND_DEG);
  t = constrain(t, 0.0f, 1.0f);

  const float power = MIN_ACTIVE_POWER_PERCENT + t * (100.0f - MIN_ACTIVE_POWER_PERCENT);
  setEscPower(power);
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server) override {
    (void)server;
    bleConnected = true;
    notifyBleStatus("connected");
  }

  void onDisconnect(BLEServer* server) override {
    bleConnected = false;
    server->getAdvertising()->start();
  }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    std::string value = characteristic->getValue();
    if (value.empty()) {
      return;
    }

    lastBleRxHex = toHexString(value);
    lastBleRxText = toPrintableString(value);
    Serial.printf("BLE_RX: len=%u text=\"%s\" hex=\"%s\"\r\n",
                  static_cast<unsigned>(value.size()),
                  lastBleRxText.c_str(),
                  lastBleRxHex.c_str());

    const String cmd = bytesToCommand(value);
    if (cmd.length() == 0) {
      notifyBleStatus("err:empty");
      return;
    }

    executeCommand(cmd, CommandSource::Ble);
  }
};

void setupBle() {
  BLEDevice::init(DEVICE_NAME);
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService* service = server->createService(SERVICE_UUID);

  BLECharacteristic* commandCharacteristic = service->createCharacteristic(
      COMMAND_UUID,
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR | BLECharacteristic::PROPERTY_READ);

  statusCharacteristic = service->createCharacteristic(
      STATUS_UUID,
      BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ);
  statusCharacteristic->addDescriptor(new BLE2902());

  commandCharacteristic->setCallbacks(new CommandCallbacks());
  commandCharacteristic->setValue("0");
  statusCharacteristic->setValue("boot");

  service->start();
  server->getAdvertising()->start();
}

void setup() {
  Serial.begin(115200);
  delay(200);

  ESP32PWM::allocateTimer(0);
  esc.setPeriodHertz(ESC_RATE_HZ);
  esc.attach(ESC_PIN, ESC_MIN_US, ESC_MAX_US);
  setEscPower(0.0f);

  Serial.println("ESC: boot hold 1000us for 3s...");
  const uint32_t bootArmStartMs = millis();
  while (millis() - bootArmStartMs < ESC_ARM_HOLD_MS) {
    delay(20);
  }
  escArmed = true;
  Serial.println("ESC: armed");

  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  Wire.setClock(50000);
  Wire.setTimeOut(30);

  if (tryInitImu(true)) {
    Serial.printf("IMU: detected addr=0x%02X whoami=0x%02X\r\n", imuAddr, imuWhoAmI);
  } else {
    imuReady = false;
    Serial.printf("IMU: not ready (addr=0x%02X whoami=0x%02X)\r\n", imuAddr, imuWhoAmI);
    Serial.print("I2C: ");
    Serial.println(scanI2CBus());
  }

  setupBle();

  Serial.println("ESC NAV ready");
  Serial.println("BLE commands: target N, t N, power N, arm, stop, status");
  printSerialHelp();
}

void loop() {
  updateImu();
  updateEscControl();
  handleSerial();

  if (streamEnabled && millis() - lastStreamMs >= 200) {
    lastStreamMs = millis();
    Serial.println(buildStatusLine());
  }

  delay(10);
}
