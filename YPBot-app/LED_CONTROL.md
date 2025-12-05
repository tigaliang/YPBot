# LED 灯光控制功能说明

## 功能概述

YPBot 应用新增了 LED 灯光控制功能，可以通过蓝牙指令控制小车上的 RGB LED 灯颜色。

## 功能特点

### 1. RGB 颜色调节
- **红色滑块 (R)**：范围 0-255
- **绿色滑块 (G)**：范围 0-255
- **蓝色滑块 (B)**：范围 0-255
- 实时颜色预览框，可视化显示当前选择的颜色

### 2. 预设颜色快捷按钮

应用提供 8 种预设颜色，一键设置：

| 颜色 | RGB 值 | 说明 |
|------|--------|------|
| 🔴 红色 | (255, 0, 0) | 纯红色 |
| 🟢 绿色 | (0, 255, 0) | 纯绿色 |
| 🔵 蓝色 | (0, 0, 255) | 纯蓝色 |
| 🟡 黄色 | (255, 255, 0) | 红+绿 |
| 🟣 紫色 | (255, 0, 255) | 红+蓝 |
| 🩵 青色 | (0, 255, 255) | 绿+蓝 |
| ⚪ 白色 | (255, 255, 255) | 全亮 |
| ⚫ 关闭 | (0, 0, 0) | 全灭 |

### 3. 发送按钮
- 点击"发送颜色"按钮，将当前选择的颜色通过蓝牙发送到小车
- 仅在蓝牙已连接时可用

## 蓝牙协议

### 命令格式
```
B|<R>|<G>|<B>$
```

### 参数说明
- `R`: 红色值，整数，范围 0-255
- `G`: 绿色值，整数，范围 0-255
- `B`: 蓝色值，整数，范围 0-255
- 命令以 `B|` 开头，以 `$` 结尾
- 三个颜色值用 `|` 分隔

### 命令示例

```
B|255|0|0$      → 红色
B|0|255|0$      → 绿色
B|0|0|255$      → 蓝色
B|255|255|0$    → 黄色
B|255|0|255$    → 紫色
B|0|255|255$    → 青色
B|255|255|255$  → 白色
B|0|0|0$        → 关闭
B|128|64|200$   → 自定义颜色
```

## 硬件配置

### 方案一：共阴 RGB LED（推荐新手）

#### 硬件连接
```
RGB LED (共阴):
  R (红色引脚) -> Arduino Pin 2 (PWM) -> 220Ω 电阻
  G (绿色引脚) -> Arduino Pin 4 (PWM) -> 220Ω 电阻
  B (蓝色引脚) -> Arduino Pin 7 (PWM) -> 220Ω 电阻
  GND (公共阴极) -> GND
```

#### Arduino 代码（已包含在示例中）
```cpp
const int LED_RED_PIN = 2;
const int LED_GREEN_PIN = 4;
const int LED_BLUE_PIN = 7;

void setLedColor(int red, int green, int blue) {
  analogWrite(LED_RED_PIN, red);
  analogWrite(LED_GREEN_PIN, green);
  analogWrite(LED_BLUE_PIN, blue);
}
```

### 方案二：WS2812B LED 灯带

#### 硬件连接
```
WS2812B:
  VCC -> 5V
  GND -> GND
  DIN -> Arduino Pin 8
```

#### Arduino 代码（需要 FastLED 库）
```cpp
#include <FastLED.h>

#define LED_PIN     8
#define NUM_LEDS    1
CRGB leds[NUM_LEDS];

void setup() {
  // ...其他初始化代码...
  FastLED.addLeds<WS2812B, LED_PIN, GRB>(leds, NUM_LEDS);
  FastLED.setBrightness(50);
}

void setLedColor(int red, int green, int blue) {
  leds[0] = CRGB(red, green, blue);
  FastLED.show();
}
```

### 方案三：共阳 RGB LED

#### 硬件连接
```
RGB LED (共阳):
  R -> Arduino Pin 2 (PWM) -> 220Ω 电阻 -> GND
  G -> Arduino Pin 4 (PWM) -> 220Ω 电阻 -> GND
  B -> Arduino Pin 7 (PWM) -> 220Ω 电阻 -> GND
  VCC (公共阳极) -> 5V
```

#### Arduino 代码
```cpp
void setLedColor(int red, int green, int blue) {
  // 共阳 LED 需要反转 PWM 值
  analogWrite(LED_RED_PIN, 255 - red);
  analogWrite(LED_GREEN_PIN, 255 - green);
  analogWrite(LED_BLUE_PIN, 255 - blue);
}
```

## 使用步骤

1. **连接硬件**
   - 按照上述方案之一连接 RGB LED 到 Arduino

2. **上传代码**
   - 将提供的 `arduino_example.ino` 上传到 Arduino
   - 代码已包含 LED 控制功能

3. **连接蓝牙**
   - 在 App 中连接小车蓝牙设备

4. **调整颜色**
   - 使用滑块精确调节 RGB 值
   - 或点击预设颜色按钮快速选择

5. **发送命令**
   - 点击"发送颜色"按钮
   - LED 立即变为设置的颜色

## 界面截图说明

```
┌─────────────────────────────┐
│   LED 灯光控制               │
├─────────────────────────────┤
│  ┌─────────────────────┐    │
│  │   颜色预览框         │    │ ← 实时显示当前颜色
│  └─────────────────────┘    │
│                             │
│  红色 (R): 255              │
│  ━━━━━━━━━●                │ ← 红色滑块
│                             │
│  绿色 (G): 128              │
│  ━━━━━●━━━━                │ ← 绿色滑块
│                             │
│  蓝色 (B): 64               │
│  ━━●━━━━━━━                │ ← 蓝色滑块
│                             │
│  快捷颜色                    │
│  [红][绿][蓝][黄]           │ ← 预设颜色
│  [紫][青][白][关]           │
│                             │
│  ┌─────────────────────┐    │
│  │    发送颜色          │    │ ← 发送按钮
│  └─────────────────────┘    │
└─────────────────────────────┘
```

## 故障排除

### LED 不亮
1. 检查引脚连接是否正确
2. 确认电阻值合适（推荐 220Ω）
3. 检查 LED 正负极是否接反
4. 测试发送纯白色 (255,255,255)

### 颜色不正确
1. 确认是共阴还是共阳 LED
2. 共阳 LED 需要反转 PWM 值
3. 检查线路接触是否良好

### 命令无响应
1. 确认蓝牙已连接
2. 查看 Arduino 串口监视器的调试信息
3. 检查命令格式是否正确

## 扩展应用

### 1. 状态指示
```cpp
void setup() {
  // 启动时显示蓝色
  setLedColor(0, 0, 255);
}

void loop() {
  if (isRunning) {
    setLedColor(0, 255, 0);  // 运行时绿色
  } else {
    setLedColor(255, 0, 0);  // 停止时红色
  }
}
```

### 2. 呼吸灯效果
```cpp
void breathingEffect() {
  for (int i = 0; i < 255; i++) {
    setLedColor(i, 0, 255 - i);
    delay(10);
  }
}
```

### 3. 速度指示
```cpp
void updateSpeedColor() {
  int speedPercent = map(currentSpeed, 0, 255, 0, 100);
  if (speedPercent < 30) {
    setLedColor(0, 255, 0);    // 慢速：绿色
  } else if (speedPercent < 70) {
    setLedColor(255, 255, 0);  // 中速：黄色
  } else {
    setLedColor(255, 0, 0);    // 快速：红色
  }
}
```

## 总结

LED 灯光控制功能为小车增添了视觉效果，可用于：
- ✨ 装饰和美化
- 🚦 状态指示
- 🎮 互动反馈
- 🌈 氛围营造

通过简单的 RGB 控制，可以创造出丰富多彩的效果！

