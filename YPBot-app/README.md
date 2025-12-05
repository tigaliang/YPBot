# YPBot - 蓝牙小车控制器

一个用于通过蓝牙控制小车进行匀加速直线运动的 Android 应用。

## 功能特性

- 🔵 **蓝牙连接**：扫描并连接已配对的蓝牙设备
- ⚡ **加速度控制**：滑块控制加速度（范围：10-50）
- ▶️ **开始/停止**：控制小车的启动和停止
- 💡 **LED 灯光控制**：RGB 颜色选择器控制小车 LED 灯颜色
  - 支持 RGB 三色滑块精确调节（0-255）
  - 8 种预设颜色快捷按钮
  - 实时颜色预览
- 🎨 **现代化 UI**：使用 Material Design 3 和 Jetpack Compose 构建

## 技术栈

- **Kotlin** - 编程语言
- **Jetpack Compose** - 现代化 UI 框架
- **Material Design 3** - UI 设计规范
- **Bluetooth Classic** - 蓝牙通信

## 蓝牙通信协议

应用通过蓝牙串口（SPP）发送文本命令到小车：

### 命令格式

1. **开始命令**
   ```
   START:<acceleration>\n
   ```
   - `acceleration`：整数值，范围 10-50
   - 示例：`START:30\n` - 以加速度 30 开始运动

2. **停止命令**
   ```
   STOP\n
   ```
   - 立即停止小车运动

3. **LED 灯光控制命令**
   ```
   B|<R>|<G>|<B>$
   ```
   - `R`：红色值，范围 0-255
   - `G`：绿色值，范围 0-255
   - `B`：蓝色值，范围 0-255
   - 示例：`B|255|0|0$` - 设置为红色
   - 示例：`B|0|255|255$` - 设置为青色
   - 示例：`B|0|0|0$` - 关闭 LED

### 小车端要求

小车需要实现接收这些命令并执行相应的控制逻辑：

- 使用 HC-05/HC-06 等蓝牙模块
- 监听串口数据
- 解析命令并控制电机
- 实现匀加速直线运动算法

## 使用步骤

1. **配对蓝牙设备**
   - 在系统设置中，先将小车的蓝牙模块配对

2. **打开应用**
   - 授予蓝牙权限

3. **连接设备**
   - 点击"连接设备"按钮
   - 从列表中选择已配对的蓝牙设备

4. **设置加速度**
   - 使用滑块调整加速度值（10-50）

5. **控制小车**
   - 点击"开始"按钮，小车开始运动
   - 运动过程中可以实时调整加速度
   - 点击"停止"按钮，小车停止

## 权限要求

应用需要以下权限：

- `BLUETOOTH` - 蓝牙基本功能
- `BLUETOOTH_ADMIN` - 蓝牙管理（Android 12 以下）
- `BLUETOOTH_CONNECT` - 蓝牙连接（Android 12+）
- `BLUETOOTH_SCAN` - 蓝牙扫描（Android 12+）

## 界面预览

应用包含四个主要功能区域：

1. **蓝牙连接区域**
   - 显示当前连接状态
   - 连接/断开按钮
   - 蓝牙图标动态显示连接状态

2. **加速度控制区域**
   - 大字体显示当前加速度值
   - 滑块控制（10-50）
   - 实时更新到运行中的小车

3. **控制按钮区域**
   - 开始按钮（绿色）
   - 停止按钮（红色）
   - 按钮有禁用状态和动画效果

4. **LED 灯光控制区域**
   - RGB 三色滑块（0-255）
   - 实时颜色预览框
   - 8 种预设颜色快捷按钮
     - 红色、绿色、蓝色、黄色
     - 紫色、青色、白色、关闭
   - 发送颜色按钮

## 构建说明

### 环境要求

- Android Studio Arctic Fox 或更高版本
- Gradle 8.0+
- Kotlin 1.9+
- Android SDK 24+ (最低)
- Android SDK 36 (目标)

### 构建步骤

```bash
# 克隆项目
cd /path/to/project

# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease
```

## 小车端参考实现（Arduino）

```cpp
#include <SoftwareSerial.h>

SoftwareSerial BTSerial(10, 11); // RX, TX

int currentAccel = 0;
bool isRunning = false;

void setup() {
  Serial.begin(9600);
  BTSerial.begin(9600);
  // 初始化电机引脚
}

void loop() {
  if (BTSerial.available()) {
    String command = BTSerial.readStringUntil('\n');
    
    if (command.startsWith("START:")) {
      currentAccel = command.substring(6).toInt();
      isRunning = true;
      startMotion(currentAccel);
    } 
    else if (command == "STOP") {
      isRunning = false;
      stopMotion();
    }
  }
  
  if (isRunning) {
    updateMotion(); // 实现匀加速运动
  }
}

void startMotion(int accel) {
  // 实现匀加速直线运动
}

void stopMotion() {
  // 停止电机
}

void updateMotion() {
  // 更新运动状态
}
```

## 许可证

MIT License

## 作者

YPPhy

## 更新日志

### v1.0.0 (2025-12-04)
- 初始版本
- 实现蓝牙连接功能
- 实现加速度控制
- 实现开始/停止控制
- 现代化 UI 设计

