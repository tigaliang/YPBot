package me.ypphy.ypbot.data.model

import android.bluetooth.BluetoothDevice

/**
 * 蓝牙设备信息数据类
 * @param device 原始蓝牙设备对象
 * @param name 设备名称
 * @param address 设备MAC地址
 * @param isPaired 是否已配对
 * @param rssi 信号强度 (dBm)
 * @param isBle 是否为BLE设备
 */
data class BluetoothDeviceInfo(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val isPaired: Boolean,
    val rssi: Int? = null,
    val isBle: Boolean = false
)

