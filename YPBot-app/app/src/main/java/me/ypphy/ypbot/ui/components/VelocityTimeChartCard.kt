package me.ypphy.ypbot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ypphy.ypbot.ui.theme.CardBackground
import me.ypphy.ypbot.ui.theme.PrimaryBlue
import me.ypphy.ypbot.ui.theme.TextPrimary
import me.ypphy.ypbot.ui.theme.TextSecondary
import me.ypphy.ypbot.ui.theme.YPBotTheme
import me.ypphy.ypbot.ui.viewmodel.CarControlViewModel.VelocityDataPoint

@Composable
fun VelocityTimeChartCard(
    velocityHistory: List<VelocityDataPoint>
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
                text = "时间-速度图表",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            // 计算时间范围（显示最近35秒的数据，或所有数据如果少于35秒）
            val maxTime = velocityHistory.maxOfOrNull { it.time } ?: 35000L
            val timeRange = maxOf(maxTime, 35000L) // 至少显示35秒
            val minTime = maxOf(0L, maxTime - timeRange)
            
            // 速度范围固定为 0-120
            val velocityMin = 0
            val velocityMax = 120
            
            // 图表尺寸
            val chartHeight = 200.dp
            
            VelocityTimeChart(
                data = velocityHistory,
                timeRange = timeRange,
                minTime = minTime,
                velocityMin = velocityMin,
                velocityMax = velocityMax,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .padding(top = 4.dp)
            )
            
            // 显示坐标轴标签
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "横轴 (t): 0-35s",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = "纵轴 (v): 0-120",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun VelocityTimeChart(
    data: List<VelocityDataPoint>,
    timeRange: Long,
    minTime: Long,
    velocityMin: Int,
    velocityMax: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val padding = 12.dp.toPx()
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2
        
        // 绘制坐标轴
        val axisColor = TextSecondary.copy(alpha = 0.5f)
        val gridColor = TextSecondary.copy(alpha = 0.2f)
        
        // X轴（时间轴）
        drawLine(
            color = axisColor,
            start = Offset(padding, size.height - padding),
            end = Offset(size.width - padding, size.height - padding),
            strokeWidth = 2.dp.toPx()
        )
        
        // Y轴（速度轴）
        drawLine(
            color = axisColor,
            start = Offset(padding, padding),
            end = Offset(padding, size.height - padding),
            strokeWidth = 2.dp.toPx()
        )
        
        // 绘制网格线
        // 水平网格线（速度）
        for (i in 0..5) {
            val y = padding + (chartHeight / 5) * i
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(size.width - padding, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        // 垂直网格线（时间）
        for (i in 0..5) {
            val x = padding + (chartWidth / 5) * i
            drawLine(
                color = gridColor,
                start = Offset(x, padding),
                end = Offset(x, size.height - padding),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        // 绘制数据点连线（仅在数据不为空时）
        if (data.size > 1) {
            val path = Path()
            var isFirst = true
            
            for (point in data) {
                val x = padding + ((point.time - minTime).toFloat() / timeRange) * chartWidth
                val y = size.height - padding - ((point.velocity - velocityMin).toFloat() / (velocityMax - velocityMin)) * chartHeight
                
                if (isFirst) {
                    path.moveTo(x, y)
                    isFirst = false
                } else {
                    path.lineTo(x, y)
                }
            }
            
            // 绘制连线
            drawPath(
                path = path,
                color = PrimaryBlue,
                style = Stroke(width = 1.dp.toPx())
            )
            
            // 绘制数据点
            for (point in data) {
                val x = padding + ((point.time - minTime).toFloat() / timeRange) * chartWidth
                val y = size.height - padding - ((point.velocity - velocityMin).toFloat() / (velocityMax - velocityMin)) * chartHeight
                
                drawCircle(
                    color = PrimaryBlue,
                    radius = 2.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        } else if (data.size == 1) {
            // 只有一个数据点时，只绘制一个点
            val point = data[0]
            val x = padding + ((point.time - minTime).toFloat() / timeRange) * chartWidth
            val y = size.height - padding - ((point.velocity - velocityMin).toFloat() / (velocityMax - velocityMin)) * chartHeight
            
            drawCircle(
                color = PrimaryBlue,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VelocityTimeChartCardPreviewEmpty() {
    YPBotTheme {
        VelocityTimeChartCard(
            velocityHistory = emptyList()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VelocityTimeChartCardPreviewWithData() {
    YPBotTheme {
        // 生成示例数据：模拟30秒内的速度变化
        val sampleData = (0..30).map { second ->
            val time = second * 1000L // 转换为毫秒
            // 模拟速度从0逐渐增加到80，然后下降
            val velocity = when {
                second < 10 -> (second * 8) // 0-80
                second < 20 -> 80 - ((second - 10) * 4) // 80-40
                else -> 40 + ((second - 20) * 3) // 40-70
            }.coerceIn(0, 100)
            VelocityDataPoint(time, velocity)
        }
        
        VelocityTimeChartCard(
            velocityHistory = sampleData
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VelocityTimeChartCardPreviewLongData() {
    YPBotTheme {
        // 生成示例数据：模拟35秒内的速度变化，包含更多数据点
        val sampleData = (0..35 step 2).map { second ->
            val time = second * 1000L // 转换为毫秒
            // 模拟正弦波速度变化
            val velocity = (50 + 30 * kotlin.math.sin(second * 0.2)).toInt().coerceIn(0, 100)
            VelocityDataPoint(time, velocity)
        }
        
        VelocityTimeChartCard(
            velocityHistory = sampleData
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VelocityTimeChartCardPreviewAccelerateToMax() {
    YPBotTheme {
        // 生成示例数据：前30秒速度从0增加到100，然后保持100不变
        val sampleData = (0..35).map { second ->
            val time = second * 1000L // 转换为毫秒
            // 前30秒从0线性增加到100，30秒后保持100
            val velocity = if (second <= 30) {
                (second * 100 / 30).coerceIn(0, 100) // 0-30秒：0到100线性增长
            } else {
                100 // 30秒后：保持100
            }
            VelocityDataPoint(time, velocity)
        }
        
        VelocityTimeChartCard(
            velocityHistory = sampleData
        )
    }
}

