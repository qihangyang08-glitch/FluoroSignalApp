package com.example.fluorosignalapp

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi


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

    // ============ 增强字段 ============

    /**
     * 中位数
     */
    val median: Double = 0.0,

    /**
     * 偏度（Skewness）- 分布的非对称性
     * 正偏度表示右侧尾部更长，负偏度表示左侧尾部更长
     */
    val skewness: Double = 0.0,

    /**
     * 峰度（Kurtosis）- 分布的尖锐程度
     * 标准化后：正值表示分布尖锐，负值表示分布平缓
     */
    val kurtosis: Double = 0.0,

    /**
     * 有效像素数（排除极端值后）
     */
    val validPixelCount: Int = 0,

    /**
     * 红色通道的统计数据（可选）
     */
    val redMean: Double? = null,
    val redStdDev: Double? = null,
    val redSnr: Double? = null,

    /**
     * 蓝色通道的统计数据（可选）
     */
    val blueMean: Double? = null,
    val blueStdDev: Double? = null,
    val blueSnr: Double? = null,

    /**
     * 分析区域（ROI）信息
     * 格式: "Rect(x, y, width, height)"
     */
    val analysisRegion: String? = null,

    /**
     * 警告信息列表
     */
    val warnings: List<String> = emptyList()
) {
    /**
     * 美化输出，便于查看分析结果
     */
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("=== Analysis Result: $imageName ===\n")
        sb.append("Timestamp: $timestamp\n")
        sb.append("\n【主要统计指标】\n")
        sb.append("Mean: ${String.format("%.2f", mean)}\n")
        sb.append("StdDev: ${String.format("%.2f", stdDev)}\n")
        sb.append("SNR: ${String.format("%.2f", snr)}\n")
        sb.append("Variance: ${String.format("%.2f", variance)}\n")

        sb.append("\n【像素信息】\n")
        sb.append("Min Pixel: $minPixelValue\n")
        sb.append("Max Pixel: $maxPixelValue\n")
        sb.append("Median: ${String.format("%.2f", median)}\n")
        sb.append("Valid Pixels: $validPixelCount\n")

        sb.append("\n【高级统计】\n")
        sb.append("Skewness: ${String.format("%.4f", skewness)}\n")
        sb.append("Kurtosis: ${String.format("%.4f", kurtosis)}\n")

        if (analysisRegion != null) {
            sb.append("\n【分析区域】\n")
            sb.append("ROI: $analysisRegion\n")
        }

        if (redMean != null) {
            sb.append("\n【红色通道】\n")
            sb.append("Mean: ${String.format("%.2f", redMean)}\n")
            sb.append("StdDev: ${String.format("%.2f", redStdDev ?: 0.0)}\n")
            sb.append("SNR: ${String.format("%.2f", redSnr ?: 0.0)}\n")
        }

        if (blueMean != null) {
            sb.append("\n【蓝色通道】\n")
            sb.append("Mean: ${String.format("%.2f", blueMean)}\n")
            sb.append("StdDev: ${String.format("%.2f", blueStdDev ?: 0.0)}\n")
            sb.append("SNR: ${String.format("%.2f", blueSnr ?: 0.0)}\n")
        }

        if (warnings.isNotEmpty()) {
            sb.append("\n【警告信息】\n")
            warnings.forEach { warning ->
                sb.append("  $warning\n")
            }
        }

        return sb.toString()
    }

    /**
     * 以 JSON 格式导出结果（便于保存或上传）
     */
    fun toJson(): String {
        return """{
    "imageName": "$imageName",
    "timestamp": $timestamp,
    "greenChannel": {
        "mean": $mean,
        "stdDev": $stdDev,
        "snr": $snr,
        "variance": $variance,
        "median": $median,
        "skewness": $skewness,
        "kurtosis": $kurtosis,
        "minPixel": $minPixelValue,
        "maxPixel": $maxPixelValue,
        "validPixels": $validPixelCount
    },
    "analysisRegion": "$analysisRegion",
    "warnings": ${warnings.joinToString(",", "[", "]") { "\"$it\"" }}
}"""
    }

    /**
     * 检查结果是否有严重异常
     */
    fun hasCriticalWarnings(): Boolean {
        return warnings.any {
            it.contains("过暗") || it.contains("过亮") || it.contains("信噪比低")
        }
    }

    /**
     * 获取数据质量评分（0-100）
     */
    fun getQualityScore(): Int {
        var score = 100

        // 根据不同因素扣分
        if (mean < 20.0 || mean > 235.0) score -= 30
        if (snr < 2.0) score -= 25
        if (stdDev < 5.0) score -= 20
        if (kotlin.math.abs(skewness) > 2.0) score -= 15
        if (validPixelCount > 0 && validPixelCount < 0.5 * 1920 * 1080) score -= 10

        return score.coerceIn(0, 100)
    }
}
