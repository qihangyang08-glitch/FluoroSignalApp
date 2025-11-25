package com.example.fluorosignalapp

import android.annotation.SuppressLint
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalSerializationApi::class)

data class AnalysisResult(
    // --- Basic Fields ---
    val imageName: String,
    val timestamp: Long,
    val mean: Double,
    val stdDev: Double,
    val snr: Double,
    val variance: Double,
    val minPixelValue: Int,
    val maxPixelValue: Int,

    // --- Partner's Advanced Fields (Target Schema) ---
    val median: Double = 0.0,
    val skewness: Double = 0.0,
    val kurtosis: Double = 0.0,
    val validPixelCount: Int = 0,
    
    // Optional Multi-channel stats
    val redMean: Double? = null,
    val redStdDev: Double? = null,
    val redSnr: Double? = null,
    
    val blueMean: Double? = null,
    val blueStdDev: Double? = null,
    val blueSnr: Double? = null,
    
    val analysisRegion: String? = null,
    val warnings: List<String> = emptyList(),

    // --- MY Custom Fields (Must Keep) ---
    val quality: ImageQuality = ImageQuality.UNKNOWN,
    val diagnosis: String = ""
)

enum class ImageQuality {
    GOOD,
    WARNING,
    BAD,
    UNKNOWN
}
