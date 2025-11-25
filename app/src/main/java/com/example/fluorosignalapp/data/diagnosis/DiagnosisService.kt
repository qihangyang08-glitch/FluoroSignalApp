package com.example.fluorosignalapp.data.diagnosis

import com.example.fluorosignalapp.AnalysisResult
import com.example.fluorosignalapp.ImageQuality

/**
 * 诊断服务 - 融合后的版本
 * 
 * 特点：
 * 1. 利用 ImageAnalyzer 计算的高级指标（warnings, median, skewness, kurtosis）
 * 2. 使用 getQualityScore() 获取综合质量评分
 * 3. 结合 ISP 绕过后的原始数据特性进行诊断
 * 4. 生成用户友好的中文诊断文本
 */
object DiagnosisService {

    fun diagnose(rawResult: AnalysisResult): AnalysisResult {
        var quality: ImageQuality
        var diagnosis: String

        // 第一阶段：基于 warnings 列表和关键指标的严重性判断
        val diagnosticInfo = generateDiagnosticInfo(rawResult)
        
        // 第二阶段：根据综合评分和特定条件确定质量等级
        when {
            // P0: 严重过曝（对于 ISP 绕过后的原始数据，>240 已经非常危险）
            rawResult.maxPixelValue >= 245 -> {
                quality = ImageQuality.BAD
                diagnosis = "🔴 严重过曝：图像信号已饱和，测量结果不可信。\n建议：立即降低曝光时间或 ISO。"
            }
            
            // P1: 接近过曝
            rawResult.maxPixelValue >= 230 -> {
                quality = ImageQuality.WARNING
                diagnosis = "🟡 接近饱和：最大像素值过高，线性度可能受影响。\n建议：适当降低曝光或 ISO，确保线性测量。"
            }
            
            // P2: 严重欠曝（对于原始数据，<15 表示信号太弱）
            rawResult.mean < 15.0 -> {
                quality = ImageQuality.BAD
                diagnosis = "🔴 严重欠曝：图像信号过弱，噪声占主导。\n建议：增加曝光时间或 ISO 以获得更强的荧光信号。"
            }
            
            // P3: 信噪比严重不足
            rawResult.snr < 2.0 -> {
                quality = ImageQuality.BAD
                diagnosis = "🔴 信噪比过低：噪声干扰严重，测量不可靠。\n建议：提高曝光或改善光学系统的信号采集。"
            }
            
            // P4: 中等质量问题
            rawResult.snr < 4.0 || rawResult.mean < 30.0 -> {
                quality = ImageQuality.WARNING
                diagnosis = "🟡 质量欠佳：图像信噪比或亮度不理想。\n建议：${diagnosticInfo.suggestions.firstOrNull() ?: "调整曝光参数改善图像。"}"
            }
            
            // P5: 偏度异常（分布严重不对称）
            kotlin.math.abs(rawResult.skewness) > 3.0 -> {
                quality = ImageQuality.WARNING
                diagnosis = "🟡 分布异常：图像灰度分布严重不对称（skewness=${String.format("%.3f", rawResult.skewness)})。\n建议：检查是否有部分区域过曝或欠曝。"
            }
            
            // P6: 存在多个警告信息
            diagnosticInfo.warningCount >= 3 -> {
                quality = ImageQuality.WARNING
                diagnosis = "🟡 多重异常检测：\n${diagnosticInfo.warningsText}\n建议：重新调整参数后重新采集。"
            }
            
            // P7: 存在 1-2 个警告，但不严重
            diagnosticInfo.warningCount >= 1 -> {
                quality = ImageQuality.WARNING
                diagnosis = "🟡 轻微异常：\n${diagnosticInfo.warningsText}\n建议：${diagnosticInfo.suggestions.firstOrNull() ?: "监控后续测量结果。"}"
            }
            
            // P8: 使用综合评分判断最终质量
            rawResult.getQualityScore() >= 80 -> {
                quality = ImageQuality.GOOD
                diagnosis = "✅ 质量良好：图像各项指标正常，测量可信。\n" +
                        "统计：Mean=${String.format("%.1f", rawResult.mean)}, " +
                        "SNR=${String.format("%.2f", rawResult.snr)}, " +
                        "Median=${String.format("%.1f", rawResult.median)}"
            }
            
            rawResult.getQualityScore() >= 60 -> {
                quality = ImageQuality.WARNING
                diagnosis = "🟡 中等质量：图像可用，但建议监控特殊情况。\n质量评分: ${rawResult.getQualityScore()}/100"
            }
            
            else -> {
                quality = ImageQuality.BAD
                diagnosis = "🔴 质量差：综合评分过低，建议重新采集。\n质量评分: ${rawResult.getQualityScore()}/100"
            }
        }

        return rawResult.copy(quality = quality, diagnosis = diagnosis)
    }

    /**
     * 生成诊断信息，包括警告文本和建议
     */
    private fun generateDiagnosticInfo(result: AnalysisResult): DiagnosticInfo {
        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        // 分析 warnings 列表
        for (warning in result.warnings) {
            when {
                warning.contains("过暗") -> {
                    warnings.add("• 图像过暗，需增加曝光")
                    suggestions.add("增加曝光时间或 ISO 值")
                }
                warning.contains("过亮") -> {
                    warnings.add("• 图像过亮，接近饱和")
                    suggestions.add("降低曝光时间或 ISO 值")
                }
                warning.contains("信噪比") -> {
                    warnings.add("• 信噪比低于阈值")
                    suggestions.add("改善光学系统或增加积分时间")
                }
                warning.contains("方差") -> {
                    warnings.add("• 像素方差过小，可能噪声或单色")
                    suggestions.add("检查图像是否为有效荧光信号")
                }
                warning.contains("分布") -> {
                    warnings.add("• 灰度分布严重不对称")
                    suggestions.add("检查样品位置和光学对齐")
                }
                warning.contains("比例") -> {
                    warnings.add("• 有效像素比例低")
                    suggestions.add("检查 ROI 设置或样品覆盖范围")
                }
            }
        }

        // RGB 通道比例异常检查
        if (result.redMean != null && result.blueMean != null && result.mean > 0) {
            val rRatio = result.redMean!! / result.mean
            val bRatio = result.blueMean!! / result.mean
            
            // 绿色通道应该是主要信号（对于荧光测量）
            if (rRatio > 1.2) {
                warnings.add("• 红通道异常高，可能影响线性度")
                suggestions.add("检查样品的 RGB 成分比例是否正常")
            }
            if (bRatio > 1.2) {
                warnings.add("• 蓝通道异常高，可能影响测量")
                suggestions.add("检查光学滤波器是否正确")
            }
        }

        // 饱和状态检查
        if (result.isSaturated) {
            warnings.add("• 检测到像素饱和，线性度受损")
            suggestions.add("立即降低曝光以恢复线性")
        }

        return DiagnosticInfo(
            warningCount = warnings.size,
            warningsText = warnings.joinToString("\n"),
            suggestions = suggestions
        )
    }

    /**
     * 诊断信息数据类
     */
    private data class DiagnosticInfo(
        val warningCount: Int,
        val warningsText: String,
        val suggestions: List<String>
    )
}
