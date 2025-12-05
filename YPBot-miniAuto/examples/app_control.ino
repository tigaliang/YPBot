/**
 * @file app_control2.ino
 * @brief 简化版小车控制程序
 * @version V1.0
 *
 * @attention main函数中不可以做任何阻塞处理！
 * 
 * 指令说明：
 * 1. B|<r>|<g>|<b>|$ - 控制LED颜色，r/g/b范围0-255
 * 2. A|<a>|$ - 匀加速直线运动，a为加速度(10-50)
 * 3. S|$ - 停止小车
 */

#include <Arduino.h>
#include "FastLED.h"
#include "Ultrasound.h"

static CRGB rgbs[1];

/* 小车运动控制参数 */
static float current_velocity = 0.0;      /* 当前速度 */
static uint8_t acceleration = 0;           /* 加速度值 (10-50) */
static bool is_accelerating = false;      /* 是否正在加速 */
static uint8_t car_direction = 0;         /* 小车移动角度，0度表示向前 */
static int8_t car_rot = 0;                /* 小车角速度，直线运动时为0 */

/* 时间控制 */
static uint32_t previousTime_ms = 0;      /* 上一次更新时间 */
const static uint32_t update_interval_ms = 50;  /* 速度更新间隔50ms */
static uint32_t previousSendTime_ms = 0;  /* 上一次发送数据时间 */
const static uint32_t send_interval_ms = 100;   /* 数据发送间隔100ms */
const static uint32_t send_interval_ms_idle = 3000;   /* 数据发送间隔3000ms */

/* 电压监测相关参数 */
static float voltage;                      /* 电压值（伏特） */
static int voltage_send;                   /* 电压值（毫伏） */

String rec_data[4];                       /* 接收串口数据 */
char *charArray;

/* 引脚定义 */
const static uint8_t ledPin = 2;
const static uint8_t motorpwmPin[4] = { 10, 9, 6, 11};
const static uint8_t motordirectionPin[4] = { 12, 8, 7, 13};

const static int pwmFrequency = 500;                /* PWM频率，单位是赫兹 */
const static int period = 10000000 / pwmFrequency;  /* PWM周期，单位是微秒 */
static uint32_t previousTime_us = 0;      /* 上一次的微秒计数时间间隔 用于非阻塞延时 */

Ultrasound ultrasound;  /* 实例化超声波 */

/* 函数声明 */
void Motor_Init(void);
void Task_Dispatcher(void);
void Rgb_Show(uint8_t rValue, uint8_t gValue, uint8_t bValue);
void Velocity_Controller(uint16_t angle, uint8_t velocity, int8_t rot);
void Motors_Set(int8_t Motor_0, int8_t Motor_1, int8_t Motor_2, int8_t Motor_3);
void PWM_Out(uint8_t PWM_Pin, int8_t DutyCycle);
void Update_Velocity(void);
void Rgb_Task(void);
void Accelerate_Task(void);
void Stop_Task(void);
void Send_Data(void);

void setup() {
  Serial.begin(9600);
  FastLED.addLeds<WS2812, ledPin, RGB>(rgbs, 1);
  Motor_Init();
  Rgb_Show(0, 255, 0);
  previousTime_ms = millis();
  previousSendTime_ms = millis();
  ultrasound.Color(0, 0, 0, 0, 0, 0);
  /* 初始化电压值 */
  voltage_send = analogRead(A3) * 0.02989 * 1000;  /* 电压计算（毫伏） */
}

void loop() {
  Task_Dispatcher();
  if(is_accelerating) {
    Update_Velocity();
  }
  Velocity_Controller(car_direction, (uint8_t)current_velocity, car_rot);
  Send_Data(false);
}

/**
 * @brief 任务调度器，解析串口指令
 */
void Task_Dispatcher(void) 
{
  uint8_t index = 0;
  while (Serial.available() > 0) 
  {
    String cmd = Serial.readStringUntil('$');

    /* 解析指令，按'|'分割 */
    while (cmd.indexOf('|') != -1) 
    {
      rec_data[index] = cmd.substring(0, cmd.indexOf('|'));
      cmd = cmd.substring(cmd.indexOf('|') + 1);
      index++;
    }
    
    charArray = rec_data[0].c_str();
    
    /* 指令B: RGB颜色控制 */
    if(strcmp(charArray, "B") == 0) 
    {
      Rgb_Task();
    }
    /* 指令A: 匀加速运动 */
    else if(strcmp(charArray, "A") == 0) 
    {
      Accelerate_Task();
    }
    /* 指令S: 停止 */
    else if(strcmp(charArray, "S") == 0) 
    {
      Stop_Task();
    }
  }
}

/**
 * @brief RGB颜色控制任务
 * 指令格式: B|<r>|<g>|<b>|$
 */
void Rgb_Task(void) 
{
  uint8_t r_data, g_data, b_data;
  r_data = (uint8_t)atoi(rec_data[1].c_str());
  g_data = (uint8_t)atoi(rec_data[2].c_str());
  b_data = (uint8_t)atoi(rec_data[3].c_str());
  ultrasound.Color(r_data, g_data, b_data, r_data, g_data, b_data);
}

/**
 * @brief 匀加速运动任务
 * 指令格式: A|<a>|$
 * a为加速度，范围10-50
 */
void Accelerate_Task(void) 
{
  int a_value = atoi(rec_data[1].c_str());
  
  /* 限制加速度范围在3-30 */
  if(a_value < 3) a_value = 3;
  if(a_value > 30) a_value = 30;

  acceleration = (uint8_t)a_value;
  current_velocity = 0.0;  /* 从速度0开始 */
  is_accelerating = true;
  car_direction = 0;       /* 0度表示向前直线运动 */
  car_rot = 0;            /* 无旋转 */
  previousTime_ms = millis();  /* 重置时间基准 */
}

/**
 * @brief 停止任务
 * 指令格式: S|$
 */
void Stop_Task(void) 
{
  current_velocity = 0.0;
  acceleration = 0;
  car_rot = 0;
  Send_Data(true); /* 在停止时发送一次数据及时更新速度数据 */
  is_accelerating = false;
}

/**
 * @brief 更新速度（匀加速计算）
 * 每50ms更新一次速度: v = v0 + a*t
 * 速度上限为100
 */
void Update_Velocity(void)
{
  uint32_t currentTime_ms = millis();
  
  if(currentTime_ms - previousTime_ms >= update_interval_ms)
  {
    /* 计算时间差（秒） */
    float delta_time = (currentTime_ms - previousTime_ms) / 1000.0;
    
    /* 匀加速公式: v = v0 + a*t */
    current_velocity += acceleration * delta_time;
    
    /* 限制速度上限为100 */
    if(current_velocity > 100.0) {
      current_velocity = 100.0;
    }
    
    previousTime_ms = currentTime_ms;
  }
}

/**
 * @brief 发送速度和电压数据
 * 格式: $<velocity>,<voltage>$
 * 每100ms发送一次
 */
void Send_Data(bool force_send)
{
  uint32_t currentTime_ms = millis();
  
  if(currentTime_ms - previousSendTime_ms >= (is_accelerating ? send_interval_ms : send_interval_ms_idle) || force_send)
  {
    /* 不运动时才重新计算电压值 */
    if (!is_accelerating) {
      voltage = analogRead(A3) * 0.02989;           /* 电压计算（伏特） */
      voltage_send = (int)(voltage * 1000);         /* 转换为毫伏 */
    }
    
    /* 获取当前速度（整数） */
    int velocity_int = (int)current_velocity;
    
    /* 发送数据: $<velocity>,<voltage>$ */
    Serial.print("$");
    Serial.print(velocity_int);
    Serial.print(",");
    Serial.print(voltage_send);
    Serial.print("$");
    
    previousSendTime_ms = currentTime_ms;
  }
}

/**
 * @brief 设置RGB灯的颜色
 * @param rValue 红色值 0-255
 * @param gValue 绿色值 0-255
 * @param bValue 蓝色值 0-255
 */
void Rgb_Show(uint8_t rValue, uint8_t gValue, uint8_t bValue) 
{
  rgbs[0].r = rValue;
  rgbs[0].g = gValue;
  rgbs[0].b = bValue;
  FastLED.show();
}

/**
 * @brief 电机初始化函数
 */
void Motor_Init(void)
{
  for(uint8_t i = 0; i < 4; i++) {
    pinMode(motordirectionPin[i], OUTPUT);
    pinMode(motorpwmPin[i], OUTPUT);
  }
  Velocity_Controller(0, 0, 0);
}

/**
 * @brief 速度控制函数
 * @param angle   用于控制小车的运动方向，小车以车头为0度方向，逆时针为正方向。
 *                取值为0~359
 * @param velocity   用于控制小车速度，取值为0~100。
 * @param rot     用于控制小车的自转速度，取值为-100~100，若大于0小车有一个逆
 *                 时针的自转速度，若小于0则有一个顺时针的自转速度。
 * @retval None
 */
void Velocity_Controller(uint16_t angle, uint8_t velocity, int8_t rot) 
{
  int8_t velocity_0, velocity_1, velocity_2, velocity_3;
  float speed = 1;
  angle += 90;
  float rad = angle * PI / 180;
  if (rot == 0) speed = 1;  /* 速度因子 */
  else speed = 0.5; 
  velocity /= sqrt(2);
  velocity_0 = (velocity * sin(rad) - velocity * cos(rad)) * speed + rot * speed;
  velocity_1 = (velocity * sin(rad) + velocity * cos(rad)) * speed - rot * speed;
  velocity_2 = (velocity * sin(rad) - velocity * cos(rad)) * speed - rot * speed;
  velocity_3 = (velocity * sin(rad) + velocity * cos(rad)) * speed + rot * speed;
  Motors_Set(velocity_0, velocity_1, velocity_2, velocity_3);
}

/**
 * @brief PWM与轮子转向设置函数
 * @param Motor_x   作为PWM与电机转向的控制数值。根据麦克纳姆轮的运动学分析求得。
 * @retval None
 */
void Motors_Set(int8_t Motor_0, int8_t Motor_1, int8_t Motor_2, int8_t Motor_3) 
{
  int8_t pwm_set[4];
  int8_t motors[4] = { Motor_0, Motor_1, Motor_2, Motor_3};
  bool direction[4] = { 1, 0, 0, 1};  /* 前进 左1 右0 */
  for(uint8_t i = 0; i < 4; ++i) 
  {
    if(motors[i] < 0) direction[i] = !direction[i];
    else direction[i] = direction[i];

    if(motors[i] == 0) pwm_set[i] = 0;
    else pwm_set[i] = abs(motors[i]);

    digitalWrite(motordirectionPin[i], direction[i]); 
    PWM_Out(motorpwmPin[i], pwm_set[i]);
  }
}

/**
 * @brief 模拟PWM输出
 * @param PWM_Pin PWM输出引脚
 * @param DutyCycle 占空比 0-100
 */
void PWM_Out(uint8_t PWM_Pin, int8_t DutyCycle)
{ 
  uint32_t currentTime_us = micros();
  int highTime = (period/100) * DutyCycle;
  int lowTime = period - highTime;

  if ((currentTime_us - previousTime_us) <= highTime) 
  {  
    digitalWrite(PWM_Pin, HIGH);
  }
  else digitalWrite(PWM_Pin, LOW);
  if (currentTime_us - previousTime_us >= period) 
  {
    previousTime_us = currentTime_us;
  }
}

