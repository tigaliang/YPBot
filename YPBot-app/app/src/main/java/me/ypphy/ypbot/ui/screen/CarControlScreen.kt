package me.ypphy.ypbot.ui.screen

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.ypphy.ypbot.data.model.BluetoothDeviceInfo
import me.ypphy.ypbot.ui.theme.AccentGreen
import me.ypphy.ypbot.ui.theme.AccentRed
import me.ypphy.ypbot.ui.theme.BackgroundLight
import me.ypphy.ypbot.ui.theme.CardBackground
import me.ypphy.ypbot.ui.theme.PrimaryBlue
import me.ypphy.ypbot.ui.theme.TextPrimary
import me.ypphy.ypbot.ui.theme.TextSecondary
import me.ypphy.ypbot.ui.theme.YPBotTheme
import me.ypphy.ypbot.ui.viewmodel.CarControlViewModel
import me.ypphy.ypbot.ui.components.VelocityTimeChartCard

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarControlScreen(
    viewModel: CarControlViewModel = viewModel()
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val carStatus by viewModel.carStatus.collectAsState()
    val velocityHistory by viewModel.velocityHistory.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "小车控制器",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BackgroundLight,
                            Color.White
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Bluetooth Connection Card
                BluetoothConnectionCard(
                    isConnected = viewModel.isConnected,
                    connectionStatus = viewModel.connectionStatus,
                    velocity = carStatus.velocity,
                    voltage = carStatus.voltage,
                    distance = carStatus.distance,
                    onConnectClick = { viewModel.showDeviceSelectionDialog() },
                    onDisconnectClick = { viewModel.disconnect() }
                )

                // Acceleration Control Card
                AccelerationControlCard(
                    acceleration = viewModel.acceleration,
                    isRunning = viewModel.isRunning,
                    isConnected = viewModel.isConnected,
                    onAccelerationChange = { viewModel.updateAcceleration(it) },
                    onToggleClick = {
                        if (viewModel.isRunning) {
                            viewModel.stopCar()
                        } else {
                            viewModel.startCar()
                        }
                    }
                )

                // Velocity-Time Chart Card (only shown when car is running)
                VelocityTimeChartCard(
                    velocityHistory = velocityHistory
                )

                // LED Color Control Card
                LedColorControlCard(
                    redValue = viewModel.redValue,
                    greenValue = viewModel.greenValue,
                    blueValue = viewModel.blueValue,
                    isConnected = viewModel.isConnected,
                    onColorChange = { r, g, b ->
                        viewModel.updateRedValue(r)
                        viewModel.updateGreenValue(g)
                        viewModel.updateBlueValue(b)
                        // 如果已连接，立即发送颜色
                        if (viewModel.isConnected) {
                            viewModel.sendLedColor()
                        }
                    }
                )
            }
        }
    }

    // Device Selection Dialog
    if (viewModel.showDeviceDialog) {
        DeviceSelectionDialog(
            discoveredDevices = discoveredDevices,
            isScanning = isScanning,
            onDismiss = { viewModel.hideDeviceSelectionDialog() },
            onScanClick = { viewModel.startDeviceScan() },
            onDeviceSelected = { viewModel.connectToDevice(it) }
        )
    }

    // Error Dialog
    viewModel.errorDialogMessage?.let { message ->
        ErrorDialog(
            title = viewModel.errorDialogTitle,
            message = message,
            onDismiss = { viewModel.dismissErrorDialog() }
        )
    }
}

@Composable
private fun BluetoothConnectionCard(
    isConnected: Boolean,
    connectionStatus: String,
    velocity: Int,
    voltage: Int,
    distance: Int,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 连接状态行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = "Bluetooth",
                        modifier = Modifier.size(32.dp),
                        tint = if (isConnected) AccentGreen else PrimaryBlue
                    )
                    Text(
                        text = connectionStatus,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) AccentGreen else TextSecondary
                    )
                }
                Button(
                    onClick = if (isConnected) onDisconnectClick else onConnectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) AccentRed else PrimaryBlue
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isConnected) "断开" else "连接",
                        fontSize = 14.sp
                    )
                }
            }

            // 实时状态显示（仅在已连接时显示）
            if (isConnected) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 速度显示
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "速度",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "$velocity",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        }
                    }

                    // 距离显示
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "距离",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "$distance",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed
                            )
                            Text(
                                text = "mm",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }

                    // 电压显示
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "电压",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = String.format("%.2f", voltage / 1000.0),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                            Text(
                                text = "V",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "未连接状态")
@Composable
private fun BluetoothConnectionCardPreviewDisconnected() {
    YPBotTheme {
        BluetoothConnectionCard(
            isConnected = false,
            connectionStatus = "未连接",
            velocity = 0,
            voltage = 0,
            distance = 0,
            onConnectClick = {},
            onDisconnectClick = {}
        )
    }
}

@Preview(showBackground = true, name = "已连接状态")
@Composable
private fun BluetoothConnectionCardPreviewConnected() {
    YPBotTheme {
        BluetoothConnectionCard(
            isConnected = true,
            connectionStatus = "已连接: YPBot",
            velocity = 50,
            voltage = 7500,
            distance = 250,
            onConnectClick = {},
            onDisconnectClick = {}
        )
    }
}

@Composable
private fun AccelerationControlCard(
    acceleration: Float,
    isRunning: Boolean,
    isConnected: Boolean,
    onAccelerationChange: (Float) -> Unit,
    onToggleClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "加速度控制",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "当前加速度: ${acceleration.toInt()}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Slider(
                value = acceleration,
                onValueChange = onAccelerationChange,
                valueRange = 3f..30f,
                steps = 27,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = PrimaryBlue,
                    activeTrackColor = PrimaryBlue,
                    inactiveTrackColor = PrimaryBlue.copy(alpha = 0.3f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("3", color = TextSecondary)
                Text("30", color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Toggle Button (Start/Stop)
            val buttonScale by animateFloatAsState(
                targetValue = 1f,
                label = "buttonScale"
            )
            Button(
                onClick = onToggleClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(buttonScale),
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) AccentRed else AccentGreen,
                    disabledContainerColor = if (isRunning) AccentRed.copy(alpha = 0.5f) else AccentGreen.copy(
                        alpha = 0.5f
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Stop" else "Start",
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "停止" else "开始",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LedColorControlCard(
    redValue: Float,
    greenValue: Float,
    blueValue: Float,
    isConnected: Boolean,
    onColorChange: (Float, Float, Float) -> Unit
) {
    // 固定饱和度和亮度为最大值，只调整色相
    val saturation = 1f
    val brightness = 1f
    
    // 使用独立的 hue 状态，避免从 RGB 重新计算导致的循环问题
    // 只在组件首次创建时从 RGB 初始化，之后完全由用户拖动控制
    var currentHue by remember {
        val hsvArray = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            redValue.toInt(),
            greenValue.toInt(),
            blueValue.toInt(),
            hsvArray
        )
        mutableStateOf(hsvArray[0])
    }
    
    // 当 RGB 值从外部改变时（非用户拖动），只在差异很大时更新 hue
    // 这样可以避免用户拖动时被覆盖，同时允许外部设置颜色
    LaunchedEffect(redValue, greenValue, blueValue) {
        val hsvArray = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            redValue.toInt(),
            greenValue.toInt(),
            blueValue.toInt(),
            hsvArray
        )
        val calculatedHue = hsvArray[0]
        
        // 计算当前 hue 对应的 RGB
        val currentRgb = android.graphics.Color.HSVToColor(floatArrayOf(currentHue, saturation, brightness))
        val currentR = android.graphics.Color.red(currentRgb)
        val currentG = android.graphics.Color.green(currentRgb)
        val currentB = android.graphics.Color.blue(currentRgb)
        
        // 只有当 RGB 值差异很大时才更新 hue（说明是外部设置，不是用户拖动）
        val rgbDiff = kotlin.math.abs(currentR - redValue.toInt()) + 
                     kotlin.math.abs(currentG - greenValue.toInt()) + 
                     kotlin.math.abs(currentB - blueValue.toInt())
        
        // 如果 RGB 差异超过阈值，说明是外部设置，更新 hue
        // 但要避免 360->0 的循环问题
        if (rgbDiff > 20 && !(currentHue > 350f && calculatedHue < 10f)) {
            currentHue = calculatedHue
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "LED 灯光控制",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 颜色选择器（只选择色相）
            ColorPicker(
                hue = currentHue,
                onHueChange = { newHue ->
                    currentHue = newHue
                    val rgb = android.graphics.Color.HSVToColor(
                        floatArrayOf(newHue, saturation, brightness)
                    )
                    onColorChange(
                        android.graphics.Color.red(rgb).toFloat(),
                        android.graphics.Color.green(rgb).toFloat(),
                        android.graphics.Color.blue(rgb).toFloat()
                    )
                }
            )
        }
    }
}

@Composable
private fun ColorPicker(
    hue: Float,
    onHueChange: (Float) -> Unit
) {
    // 色相选择器（水平渐变条）
    Text(
        text = "选择颜色",
        fontSize = 14.sp,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    val density = LocalDensity.current
    var boxWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .onGloballyPositioned { coordinates ->
                boxWidth = with(density) { coordinates.size.width.toDp() }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = (0..36).map { i ->
                            val h = i * 10f
                            val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, 1f))
                            Color(
                                android.graphics.Color.red(rgb),
                                android.graphics.Color.green(rgb),
                                android.graphics.Color.blue(rgb)
                            )
                        }
                    ),
                    RoundedCornerShape(8.dp)
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val x = change.position.x.coerceIn(0f, size.width.toFloat())
                        // 确保 hue 在 0-359.99 范围内，避免 360 导致循环回 0
                        val newHue = (x / size.width * 360f).coerceIn(0f, 359.99f)
                        onHueChange(newHue)
                    }
                }
                .clickable { }
        )
        if (boxWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(
                        x = with(LocalDensity.current) {
                            val maxOffset = (boxWidth - 16.dp).toPx()
                            val offset = (hue / 360f * maxOffset).coerceIn(0f, maxOffset)
                            offset.toDp()
                        }
                    )
                    .size(16.dp)
                    .background(Color.White, CircleShape)
                    .padding(2.dp)
                    .background(Color.Black, CircleShape)
            )
        }
    }
}

@Composable
private fun DeviceSelectionDialog(
    discoveredDevices: List<BluetoothDeviceInfo>,
    isScanning: Boolean,
    onDismiss: () -> Unit,
    onScanClick: () -> Unit,
    onDeviceSelected: (BluetoothDeviceInfo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选择蓝牙设备", fontWeight = FontWeight.Bold)
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Scan button
                Button(
                    onClick = onScanClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) AccentRed else PrimaryBlue
                    )
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Bluetooth,
                        contentDescription = if (isScanning) "停止扫描" else "扫描设备",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isScanning) "停止扫描" else "扫描设备")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Device list
                if (discoveredDevices.isEmpty() && !isScanning) {
                    Text(
                        "没有找到设备，请点击上方按钮开始扫描",
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        discoveredDevices.forEach { deviceInfo ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (deviceInfo.isPaired)
                                        AccentGreen.copy(alpha = 0.1f)
                                    else
                                        Color.White
                                )
                            ) {
                                TextButton(
                                    onClick = { onDeviceSelected(deviceInfo) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = deviceInfo.name,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 16.sp
                                            )
                                            if (deviceInfo.isPaired) {
                                                Text(
                                                    text = "已配对",
                                                    fontSize = 12.sp,
                                                    color = AccentGreen,
                                                    modifier = Modifier
                                                        .background(
                                                            AccentGreen.copy(alpha = 0.2f),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(
                                                            horizontal = 6.dp,
                                                            vertical = 2.dp
                                                        )
                                                )
                                            }
                                        }
                                        Text(
                                            text = deviceInfo.address,
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                        deviceInfo.rssi?.let { rssi ->
                                            val signalStrength = when {
                                                rssi > -50 -> "信号强"
                                                rssi > -70 -> "信号中"
                                                else -> "信号弱"
                                            }
                                            Text(
                                                text = "$signalStrength ($rssi dBm)",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = AccentRed
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(message)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

