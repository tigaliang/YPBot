package me.ypphy.ypbot.data.model

/**
 * 小车状态数据类
 * @param velocity 当前速度（整数）
 * @param voltage 电压值（整数，毫伏）
 * @param distance 超声波距离（整数，毫米）
 */
data class CarStatus(
    val velocity: Int = 0,
    val voltage: Int = 0,
    val distance: Int = 0
)

