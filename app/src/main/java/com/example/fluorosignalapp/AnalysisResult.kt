package com.example.fluorosignalapp

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * 荧光图像分析结果数据模型
 * 存储图像分析过程中产出的所有关键指标
 */
@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AnalysisResult(
    /**
     * 图片的基础名
     * 例如: FLUORO_20251107_103055123
     */
    val imageName: String,

    /**
     * 进行分析时的时间戳（毫秒）
     */
    val timestamp: Long,

    /**
     * 绿色通道的像素均值
     */
    val mean: Double,

    /**
     * 标准差
     */
    val stdDev: Double,

    /**
     * 信噪比 (Signal-to-Noise Ratio)
     * 计算公式: mean / stdDev
     */
    val snr: Double,

    /**
     * 方差
     * 计算公式: stdDev * stdDev
     */
    val variance: Double,

    /**
     * 最小像素值
     */
    val minPixelValue: Int,

    /**
     * 最大像素值
     */
    val maxPixelValue: Int,

    val quality: ImageQuality = ImageQuality.UNKNOWN,
    val diagnosis: String = ""
)

enum class ImageQuality {
    GOOD,
    WARNING,
    BAD,
    UNKNOWN
}
