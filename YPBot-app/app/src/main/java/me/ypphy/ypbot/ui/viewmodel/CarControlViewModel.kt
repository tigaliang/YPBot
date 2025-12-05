package me.ypphy.ypbot.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.ypphy.ypbot.BluetoothManager
import me.ypphy.ypbot.ConnectionResult
import me.ypphy.ypbot.data.model.BluetoothDeviceInfo

class CarControlViewModel(
    private val bluetoothManager: BluetoothManager
) : ViewModel() {

    var isConnected by mutableStateOf(false)
        private set

    var selectedDevice by mutableStateOf<BluetoothDeviceInfo?>(null)
        private set

    var acceleration by mutableFloatStateOf(15f)
        private set

    var isRunning by mutableStateOf(false)
        private set

    var showDeviceDialog by mutableStateOf(false)
        private set

    var connectionStatus by mutableStateOf("未连接")
        private set

    var errorDialogMessage by mutableStateOf<String?>(null)
        private set

    var errorDialogTitle by mutableStateOf("")
        private set

    var redValue by mutableFloatStateOf(123f)
        private set

    var greenValue by mutableFloatStateOf(123f)
        private set

    var blueValue by mutableFloatStateOf(123f)
        private set

    val discoveredDevices: StateFlow<List<BluetoothDeviceInfo>> = bluetoothManager.discoveredDevices
    val isScanning: StateFlow<Boolean> = bluetoothManager.isScanning
    val carStatus: StateFlow<me.ypphy.ypbot.data.model.CarStatus> = bluetoothManager.carStatus

    fun updateAcceleration(value: Float) {
        acceleration = value
        if (isRunning && isConnected) {
            bluetoothManager.sendStart(value.toInt())
        }
    }

    fun startCar() {
        if (isConnected) {
            isRunning = true
            bluetoothManager.sendStart(acceleration.toInt())
        }
    }

    fun stopCar() {
        if (isConnected) {
            isRunning = false
            bluetoothManager.sendStop()
        }
    }

    fun showDeviceSelectionDialog() {
        showDeviceDialog = true
    }

    fun hideDeviceSelectionDialog() {
        showDeviceDialog = false
        bluetoothManager.stopDiscovery()
    }

    fun startDeviceScan() {
        if (isScanning.value) {
            bluetoothManager.stopDiscovery()
        } else {
            bluetoothManager.startDiscovery()
        }
    }

    fun connectToDevice(deviceInfo: BluetoothDeviceInfo) {
        selectedDevice = deviceInfo
        connectionStatus = "正在连接..."
        showDeviceDialog = false
        bluetoothManager.stopDiscovery()

        CoroutineScope(Dispatchers.Main).launch {
            when (val result = bluetoothManager.connectToDevice(deviceInfo)) {
                is ConnectionResult.Success -> {
                    isConnected = true
                    connectionStatus = "已连接: ${result.deviceName}"
                }

                is ConnectionResult.Error -> {
                    isConnected = false
                    connectionStatus = "连接失败"
                    errorDialogTitle = result.message
                    errorDialogMessage = result.details ?: "未知错误"
                }
            }
        }
    }

    fun disconnect() {
        bluetoothManager.disconnect()
        isConnected = false
        connectionStatus = "未连接"
        isRunning = false
    }

    fun updateRedValue(value: Float) {
        redValue = value
    }

    fun updateGreenValue(value: Float) {
        greenValue = value
    }

    fun updateBlueValue(value: Float) {
        blueValue = value
    }

    fun sendLedColor() {
        if (isConnected) {
            bluetoothManager.sendLedColor(
                redValue.toInt(),
                greenValue.toInt(),
                blueValue.toInt()
            )
        }
    }

    fun dismissErrorDialog() {
        errorDialogMessage = null
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.disconnect()
    }
}

