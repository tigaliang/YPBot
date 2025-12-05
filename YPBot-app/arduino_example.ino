/**
 * 小车端蓝牙接收示例代码（Arduino）
 * 
 * 硬件要求：
 * - Arduino Uno/Nano/Mega
 * - HC-05 或 HC-06 蓝牙模块
 * - L298N 电机驱动模块
 * - 直流电机 x2
 * - WS2812B RGB LED 灯带 或 共阴 RGB LED
 * 
 * 连接说明：
 * HC-05/06:
 *   VCC -> 5V
 *   GND -> GND
 *   TXD -> Arduino RX (Pin 10 软串口)
 *   RXD -> Arduino TX (Pin 11 软串口，需要分压到3.3V）
 * 
 * L298N:
 *   IN1 -> Pin 5
 *   IN2 -> Pin 6
 *   IN3 -> Pin 9
 *   IN4 -> Pin 10
 *   ENA -> Pin 3 (PWM)
 *   ENB -> Pin 11 (PWM)
 * 
 * RGB LED (共阴):
 *   R -> Pin 2 (PWM)
 *   G -> Pin 4 (PWM)
 *   B -> Pin 7 (PWM)
 *   GND -> GND
 */

#include <SoftwareSerial.h>

// 蓝牙串口配置
SoftwareSerial BTSerial(10, 11); // RX, TX

// 电机引脚定义
const int MOTOR_LEFT_PIN1 = 5;
const int MOTOR_LEFT_PIN2 = 6;
const int MOTOR_LEFT_PWM = 3;

const int MOTOR_RIGHT_PIN1 = 9;
const int MOTOR_RIGHT_PIN2 = 10;
const int MOTOR_RIGHT_PWM = 11;

// LED 引脚定义 (RGB LED 共阴)
const int LED_RED_PIN = 2;
const int LED_GREEN_PIN = 4;
const int LED_BLUE_PIN = 7;

// 运动参数
int targetAcceleration = 0;  // 目标加速度 (10-50)
bool isRunning = false;      // 运行状态
float currentSpeed = 0;      // 当前速度 (0-255)
unsigned long lastUpdateTime = 0;
const int UPDATE_INTERVAL = 50; // 更新间隔（毫秒）

void setup() {
  // 初始化串口通信
  Serial.begin(9600);
  BTSerial.begin(9600);

  // 初始化电机引脚
  pinMode(MOTOR_LEFT_PIN1, OUTPUT);
  pinMode(MOTOR_LEFT_PIN2, OUTPUT);
  pinMode(MOTOR_LEFT_PWM, OUTPUT);
  pinMode(MOTOR_RIGHT_PIN1, OUTPUT);
  pinMode(MOTOR_RIGHT_PIN2, OUTPUT);
  pinMode(MOTOR_RIGHT_PWM, OUTPUT);
  
  // 初始化 LED 引脚
  pinMode(LED_RED_PIN, OUTPUT);
  pinMode(LED_GREEN_PIN, OUTPUT);
  pinMode(LED_BLUE_PIN, OUTPUT);
  
  // 初始状态：停止
  stopMotors();
  setLedColor(0, 0, 0); // LED 关闭

  Serial.println("小车蓝牙控制系统启动");
  Serial.println("等待连接...");
}

void loop() {
  // 检查蓝牙数据
  if (BTSerial.available()) {
    String command = BTSerial.readStringUntil('\n');
    command.trim(); // 去除首尾空白字符

    Serial.print("收到命令: ");
    Serial.println(command);

    handleCommand(command);
  }

  // 如果正在运行，更新运动状态
  if (isRunning) {
    unsigned long currentTime = millis();
    if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
      updateMotion();
      lastUpdateTime = currentTime;
    }
  }
}

/**
 * 处理蓝牙命令
 */
void handleCommand(String command) {
  if (command.startsWith("START:")) {
    // 解析加速度值
    int accel = command.substring(6).toInt();

    if (accel >= 10 && accel <= 50) {
      targetAcceleration = accel;
      isRunning = true;
      currentSpeed = 0; // 从零开始加速
      lastUpdateTime = millis();

      Serial.print("开始运动，加速度: ");
      Serial.println(targetAcceleration);

      BTSerial.println("OK:START");
    } else {
      Serial.println("错误：加速度超出范围");
      BTSerial.println("ERROR:INVALID_ACCELERATION");
    }
  }
  else if (command == "STOP") {
    isRunning = false;
    stopMotors();
    
    Serial.println("停止运动");
    BTSerial.println("OK:STOP");
  }
  else if (command.startsWith("B|") && command.endsWith("$")) {
    // 解析 LED 颜色命令: B|R|G|B$
    String colorData = command.substring(2, command.length() - 1); // 去掉 "B|" 和 "$"
    
    int firstDelim = colorData.indexOf('|');
    int secondDelim = colorData.indexOf('|', firstDelim + 1);
    
    if (firstDelim > 0 && secondDelim > firstDelim) {
      int red = colorData.substring(0, firstDelim).toInt();
      int green = colorData.substring(firstDelim + 1, secondDelim).toInt();
      int blue = colorData.substring(secondDelim + 1).toInt();
      
      // 限制范围 0-255
      red = constrain(red, 0, 255);
      green = constrain(green, 0, 255);
      blue = constrain(blue, 0, 255);
      
      setLedColor(red, green, blue);
      
      Serial.print("设置 LED 颜色: R=");
      Serial.print(red);
      Serial.print(", G=");
      Serial.print(green);
      Serial.print(", B=");
      Serial.println(blue);
      
      BTSerial.println("OK:LED");
    } else {
      Serial.println("错误：LED 命令格式错误");
      BTSerial.println("ERROR:INVALID_LED_FORMAT");
    }
  }
  else {
    Serial.println("未知命令");
    BTSerial.println("ERROR:UNKNOWN_COMMAND");
  }
}

/**
 * 更新运动状态（实现匀加速）
 */
void updateMotion() {
  // 计算速度增量
  // 加速度映射：10-50 -> 实际速度增量
  float speedIncrement = map(targetAcceleration, 10, 50, 1, 5) * 0.5;

  // 更新当前速度
  currentSpeed += speedIncrement;

  // 限制最大速度
  if (currentSpeed > 255) {
    currentSpeed = 255;
  }

  // 应用速度到电机
  setMotorSpeed((int)currentSpeed);

  // 调试输出
  if ((int)currentSpeed % 10 == 0) {
    Serial.print("当前速度: ");
    Serial.println((int)currentSpeed);
  }
}

/**
 * 设置电机速度
 * @param speed 速度值 (0-255)
 */
void setMotorSpeed(int speed) {
  // 限制速度范围
  speed = constrain(speed, 0, 255);

  // 设置左电机前进
  digitalWrite(MOTOR_LEFT_PIN1, HIGH);
  digitalWrite(MOTOR_LEFT_PIN2, LOW);
  analogWrite(MOTOR_LEFT_PWM, speed);

  // 设置右电机前进
  digitalWrite(MOTOR_RIGHT_PIN1, HIGH);
  digitalWrite(MOTOR_RIGHT_PIN2, LOW);
  analogWrite(MOTOR_RIGHT_PWM, speed);
}

/**
 * 停止所有电机
 */
void stopMotors() {
  currentSpeed = 0;

  digitalWrite(MOTOR_LEFT_PIN1, LOW);
  digitalWrite(MOTOR_LEFT_PIN2, LOW);
  analogWrite(MOTOR_LEFT_PWM, 0);

  digitalWrite(MOTOR_RIGHT_PIN1, LOW);
  digitalWrite(MOTOR_RIGHT_PIN2, LOW);
  analogWrite(MOTOR_RIGHT_PWM, 0);
}

/**
 * 电机自检（可在 setup() 中调用）
 */
void motorTest() {
  Serial.println("电机测试开始...");

  // 测试左电机
  Serial.println("测试左电机前进");
  digitalWrite(MOTOR_LEFT_PIN1, HIGH);
  digitalWrite(MOTOR_LEFT_PIN2, LOW);
  analogWrite(MOTOR_LEFT_PWM, 150);
  delay(2000);

  // 测试右电机
  Serial.println("测试右电机前进");
  digitalWrite(MOTOR_LEFT_PIN1, LOW);
  analogWrite(MOTOR_LEFT_PWM, 0);
  digitalWrite(MOTOR_RIGHT_PIN1, HIGH);
  digitalWrite(MOTOR_RIGHT_PIN2, LOW);
  analogWrite(MOTOR_RIGHT_PWM, 150);
  delay(2000);

  // 停止
  stopMotors();
  Serial.println("电机测试完成");
}

/**
 * 设置 LED 颜色
 * @param red 红色值 (0-255)
 * @param green 绿色值 (0-255)
 * @param blue 蓝色值 (0-255)
 */
void setLedColor(int red, int green, int blue) {
  analogWrite(LED_RED_PIN, red);
  analogWrite(LED_GREEN_PIN, green);
  analogWrite(LED_BLUE_PIN, blue);
}

/**
 * 调试：通过串口监视器手动测试
 * 在 loop() 中添加以下代码：
 * 
 * if (Serial.available()) {
 *   String cmd = Serial.readStringUntil('\n');
 *   handleCommand(cmd);
 * }
 */

