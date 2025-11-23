package com.example.fluorosignalapp.data.diagnosis

import com.example.fluorosignalapp.AnalysisResult
import com.example.fluorosignalapp.ImageQuality

object DiagnosisService {

    fun diagnose(rawResult: AnalysisResult): AnalysisResult {
        val quality: ImageQuality
        val diagnosis: String

        when {
            rawResult.maxPixelValue >= 250 -> {
                quality = ImageQuality.BAD
                diagnosis = "严重过曝，请立即降低曝光或ISO！"
            }
            rawResult.maxPixelValue >= 230 -> {
                quality = ImageQuality.WARNING
                diagnosis = "图像接近饱和，建议适当降低曝光或ISO。"
            }
            rawResult.mean < 10 -> {
                quality = ImageQuality.BAD
                diagnosis = "信号过弱，图像过暗，请增加曝光或ISO。"
            }
            rawResult.snr < 5.0 -> {
                quality = ImageQuality.WARNING
                diagnosis = "信噪比偏低，图像质量欠佳，可尝试优化参数。"
            }
            else -> {
                quality = ImageQuality.GOOD
                diagnosis = "图像质量良好，各项指标正常。"
            }
        }

        return rawResult.copy(quality = quality, diagnosis = diagnosis)
    }
}
