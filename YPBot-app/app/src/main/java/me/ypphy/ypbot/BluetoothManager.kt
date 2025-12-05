package me.ypphy.ypbot

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ypphy.ypbot.data.model.BluetoothDeviceInfo
import me.ypphy.ypbot.data.model.CarStatus
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

sealed class ConnectionResult {
    data class Success(val deviceName: String) : ConnectionResult()
    data class Error(val message: String, val details: String? = null) : ConnectionResult()
}

class BluetoothManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // BLE 相关
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val handler = Handler(Looper.getMainLooper())
    private val BLE_SCAN_PERIOD: Long = 5000 // 扫描 5 秒

    // Nordic UART Service UUIDs (常用于 BLE 串口通信)
    private val UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val UART_TX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Write
    private val UART_RX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Notify

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceInfo>> = _discoveredDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _carStatus = MutableStateFlow<CarStatus>(CarStatus())
    val carStatus: StateFlow<CarStatus> = _carStatus

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    if (!hasBluetoothPermission()) return

                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                    device?.let {
                        // 不显示名称为 null 或空的设备
                        val deviceName = it.name
                        if (deviceName.isNullOrBlank()) return@let

                        val deviceInfo = BluetoothDeviceInfo(
                            device = it,
                            name = deviceName,
                            address = it.address,
                            isPaired = it.bondState == BluetoothDevice.BOND_BONDED,
                            rssi = rssi,
                            isBle = false
                        )

                        val currentList = _discoveredDevices.value.toMutableList()
                        // Remove duplicates based on address
                        currentList.removeAll { existing -> existing.address == deviceInfo.address }
                        currentList.add(deviceInfo)
                        // Sort by signal strength (RSSI) if available
                        _discoveredDevices.value = currentList.sortedByDescending { it.rssi ?: Int.MIN_VALUE }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.value = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    // BLE 扫描回调
    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!hasBluetoothPermission()) return

            val device = result.device
            // 不显示名称为 null 或空的设备
            val deviceName = device.name
            if (deviceName.isNullOrBlank()) return

            val deviceInfo = BluetoothDeviceInfo(
                device = device,
                name = deviceName,
                address = device.address,
                isPaired = device.bondState == BluetoothDevice.BOND_BONDED,
                rssi = result.rssi,
                isBle = true
            )

            val currentList = _discoveredDevices.value.toMutableList()
            // Remove duplicates based on address
            currentList.removeAll { existing -> existing.address == deviceInfo.address }
            currentList.add(deviceInfo)
            // Sort by signal strength (RSSI) if available
            _discoveredDevices.value = currentList.sortedByDescending { it.rssi ?: Int.MIN_VALUE }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { result ->
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    // BLE GATT 回调
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    writeCharacteristic = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 尝试找到 UART 服务
                val uartService = gatt.getService(UART_SERVICE_UUID)
                if (uartService != null) {
                    writeCharacteristic = uartService.getCharacteristic(UART_TX_CHAR_UUID)
                    // 启用通知以接收数据
                    val rxCharacteristic = uartService.getCharacteristic(UART_RX_CHAR_UUID)
                    rxCharacteristic?.let { char ->
                        gatt.setCharacteristicNotification(char, true)
                        val descriptor = char.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        )
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                } else {
                    // 如果没有找到标准 UART 服务，尝试查找其他可写特征
                    for (service in gatt.services) {
                        for (characteristic in service.characteristics) {
                            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                                writeCharacteristic = characteristic
                            }
                            // 查找可通知的特征以接收数据
                            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                gatt.setCharacteristicNotification(characteristic, true)
                                val descriptor = characteristic.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                                )
                                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value
            if (data != null) {
                val message = String(data)
                // BLE 数据可能分多次接收，需要缓冲处理
                bleDataBuffer.append(message)
                
                // 查找完整的数据包（以 $ 开头和结尾）
                var startIndex = bleDataBuffer.indexOf("$")
                while (startIndex >= 0) {
                    val endIndex = bleDataBuffer.indexOf("$", startIndex + 1)
                    if (endIndex > startIndex) {
                        // 找到完整的数据包
                        val completeData = bleDataBuffer.substring(startIndex, endIndex + 1)
                        parseAndUpdateStatus(completeData)
                        // 移除已处理的数据
                        bleDataBuffer.delete(0, endIndex + 1)
                        startIndex = bleDataBuffer.indexOf("$")
                    } else {
                        // 没有找到结束标记，等待更多数据
                        break
                    }
                }
                
                // 如果缓冲区太大，清空它（防止内存泄漏）
                if (bleDataBuffer.length > 1000) {
                    bleDataBuffer.clear()
                }
            }
        }
    }


    init {
        // Register the broadcast receiver for Bluetooth discovery
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)

        // 初始化 BLE 扫描器
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!hasBluetoothPermission()) {
            return
        }

        // Clear previous discoveries
        _discoveredDevices.value = emptyList()
        _isScanning.value = true

        // Add paired devices first
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            // 不显示名称为 null 或空的设备
            val deviceName = device.name
            if (deviceName.isNullOrBlank()) return@forEach

            val deviceInfo = BluetoothDeviceInfo(
                device = device,
                name = deviceName,
                address = device.address,
                isPaired = true,
                rssi = null,
                isBle = false // 配对设备可能是经典蓝牙或 BLE
            )
            val currentList = _discoveredDevices.value.toMutableList()
            currentList.add(deviceInfo)
            _discoveredDevices.value = currentList
        }

        // 启动 BLE 扫描
        startBleScan()
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (!hasBluetoothPermission()) return

        val scanner = bluetoothLeScanner ?: bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _isScanning.value = false
            return
        }

        bluetoothLeScanner = scanner

        // 配置扫描设置
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // 开始扫描
        scanner.startScan(null, scanSettings, bleScanCallback)

        // 设置扫描超时
        handler.postDelayed({
            stopBleScan()
        }, BLE_SCAN_PERIOD)
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (!hasBluetoothPermission()) return
        bluetoothLeScanner?.stopScan(bleScanCallback)
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (!hasBluetoothPermission()) return
        // 停止经典蓝牙发现
        bluetoothAdapter?.cancelDiscovery()
        // 停止 BLE 扫描
        stopBleScan()
        // 移除待处理的停止扫描任务
        handler.removeCallbacksAndMessages(null)
        _isScanning.value = false
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(deviceInfo: BluetoothDeviceInfo): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            if (!hasBluetoothPermission()) {
                return@withContext ConnectionResult.Error(
                    "权限不足",
                    "请在系统设置中允许蓝牙权限"
                )
            }

            // Stop discovery to improve connection performance
            stopDiscovery()

            // 先断开现有连接
            disconnect()

            val device = deviceInfo.device

            // 如果是 BLE 设备，使用 GATT 连接
            if (deviceInfo.isBle) {
                return@withContext connectBleDevice(device, deviceInfo.name)
            }

            // 经典蓝牙连接 (RFCOMM)
            // 尝试方法 1: 标准 RFCOMM
            bluetoothSocket = try {
                device.createRfcommSocketToServiceRecord(uuid)
            } catch (e: Exception) {
                // 尝试方法 2: 使用反射创建不安全连接（适用于某些 HC-05 模块）
                try {
                    val method = device.javaClass.getMethod(
                        "createRfcommSocket",
                        Int::class.javaPrimitiveType
                    )
                    method.invoke(device, 1) as BluetoothSocket
                } catch (e2: Exception) {
                    return@withContext ConnectionResult.Error(
                        "无法创建连接",
                        "设备不支持 RFCOMM: ${e2.message}"
                    )
                }
            }

            // 取消发现以提高连接速度
            bluetoothAdapter?.cancelDiscovery()

            // 连接（可能需要几秒钟）
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            inputStream = bluetoothSocket?.inputStream

            // 启动数据接收线程
            startDataReceiver()

            ConnectionResult.Success(deviceInfo.name)
        } catch (e: IOException) {
            val errorMessage = when {
                e.message?.contains("Connection refused") == true -> "设备拒绝连接"
                e.message?.contains("timeout") == true -> "连接超时"
                e.message?.contains("Device or resource busy") == true -> "设备忙碌"
                e.message?.contains("read failed") == true -> "读取失败，设备可能已断开"
                e.message?.contains("socket closed") == true -> "连接已关闭"
                else -> "连接失败"
            }
            val details = buildString {
                append("错误详情: ${e.message}\n\n")
                append("可能的原因:\n")
                when {
                    e.message?.contains("Connection refused") == true -> {
                        append("• 设备未开启或不在范围内\n")
                        append("• HC-05 模块未上电\n")
                        append("• 设备正在与其他设备连接")
                    }
                    e.message?.contains("timeout") == true -> {
                        append("• 设备距离太远\n")
                        append("• 信号被干扰\n")
                        append("• 设备响应缓慢")
                    }
                    e.message?.contains("Device or resource busy") == true -> {
                        append("• 设备正在被其他应用使用\n")
                        append("• 请先断开其他连接\n")
                        append("• 尝试重启蓝牙")
                    }
                    else -> {
                        append("• 检查设备是否开启\n")
                        append("• 确认设备在蓝牙范围内\n")
                        append("• 尝试重新扫描设备")
                    }
                }
            }
            disconnect()
            ConnectionResult.Error(errorMessage, details)
        } catch (e: Exception) {
            disconnect()
            ConnectionResult.Error(
                "未知错误",
                "错误类型: ${e.javaClass.simpleName}\n详情: ${e.message}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectBleDevice(device: BluetoothDevice, deviceName: String): ConnectionResult {
        return try {
            withContext(Dispatchers.Main) {
                bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }

            // 等待服务发现完成（最多等待 10 秒）
            var attempts = 0
            while (writeCharacteristic == null && attempts < 100) {
                kotlinx.coroutines.delay(100)
                attempts++
            }

            if (writeCharacteristic != null) {
                ConnectionResult.Success(deviceName)
            } else {
                ConnectionResult.Error(
                    "BLE 连接失败",
                    "无法找到可写特征，请确保设备支持 UART 服务"
                )
            }
        } catch (e: Exception) {
            ConnectionResult.Error(
                "BLE 连接错误",
                "错误类型: ${e.javaClass.simpleName}\n详情: ${e.message}"
            )
        }
    }

    fun cleanup() {
        try {
            stopDiscovery()
            context.unregisterReceiver(discoveryReceiver)
            disconnect()
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    private var isReceiving = false
    private var bleDataBuffer = StringBuilder()

    /**
     * 启动数据接收线程（用于经典蓝牙）
     */
    private fun startDataReceiver() {
        if (isReceiving) return
        isReceiving = true
        
        scope.launch {
            val stream = inputStream ?: return@launch
            val reader = BufferedReader(InputStreamReader(stream))
            
            try {
                while (isReceiving && bluetoothSocket?.isConnected == true) {
                    val line = reader.readLine() ?: break
                    parseAndUpdateStatus(line)
                }
            } catch (e: IOException) {
                Log.e("BluetoothManager", "数据接收错误: ${e.message}")
            } finally {
                isReceiving = false
            }
        }
    }

    /**
     * 解析接收到的数据格式: $<velocity>,<voltage>$
     */
    private fun parseAndUpdateStatus(data: String) {
        try {
            // 移除所有空白字符
            val trimmed = data.trim()
            
            // 检查格式: $<velocity>,<voltage>$
            if (trimmed.startsWith("$") && trimmed.endsWith("$")) {
                val content = trimmed.substring(1, trimmed.length - 1)
                val parts = content.split(",")
                
                if (parts.size == 2) {
                    val velocity = parts[0].toIntOrNull() ?: 0
                    val voltage = parts[1].toIntOrNull() ?: 0
                    
                    _carStatus.value = CarStatus(velocity = velocity, voltage = voltage)
                    Log.d("BluetoothManager", "收到状态: velocity=$velocity, voltage=$voltage")
                }
            }
        } catch (e: Exception) {
            Log.e("BluetoothManager", "解析数据错误: ${e.message}, 数据: $data")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            isReceiving = false
            
            // 断开经典蓝牙连接
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            inputStream = null
            outputStream = null
            bluetoothSocket = null

            // 断开 BLE 连接
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            writeCharacteristic = null
            
            // 重置状态
            _carStatus.value = CarStatus()
            bleDataBuffer.clear()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true || bluetoothGatt != null
    }

    @SuppressLint("MissingPermission")
    suspend fun sendCommand(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 如果是经典蓝牙连接
            if (outputStream != null) {
                outputStream?.write(command.toByteArray())
                outputStream?.flush()
                return@withContext true
            }

            // 如果是 BLE 连接
            val characteristic = writeCharacteristic
            val gatt = bluetoothGatt
            if (characteristic != null && gatt != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        characteristic,
                        command.toByteArray(),
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = command.toByteArray()
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                }
                return@withContext true
            }

            false
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun sendStart(acceleration: Int) {
        // Format: A|<a>|$
        // 确保加速度在 3-30 范围内
        val clampedAcceleration = acceleration.coerceIn(3, 30)
        val command = "A|$clampedAcceleration|$"
        scope.launch {
            sendCommand(command)
        }
    }

    fun sendStop() {
        // Format: S|$
        val command = "S|$"
        // 停止后立即将速度设置为 0
        _carStatus.value = _carStatus.value.copy(velocity = 0)
        scope.launch {
            sendCommand(command)
        }
    }

    fun sendLedColor(red: Int, green: Int, blue: Int) {
        // Format: B|R|G|B|$
        val command = "B|$red|$green|$blue|$"
        Log.i("BluetoothManager", "Sending LED command: $command")
        scope.launch {
            sendCommand(command)
        }
    }
}
