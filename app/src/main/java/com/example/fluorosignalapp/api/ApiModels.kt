package com.example.fluorosignalapp.api

import kotlinx.serialization.Serializable

/**
 * API请求/响应数据模型 - 前后端通信契约
 */

// ===== 请求模型 =====
@Serializable
data class AnalysisRequest(
    val imageFileName: String,
    val imagePath: String,
    val roiX: Int = 500,
    val roiY: Int = 500,
    val roiWidth: Int = 800,
    val roiHeight: Int = 800
)

@Serializable
data class StorageRequest(
    val imageId: String,
    val imageData: String, // Base64编码
    val metadata: String   // JSON格式元数据
)

// ===== 响应模型 =====
@Serializable
data class AnalysisResponse(
    val success: Boolean,
    val statusCode: Int,
    val message: String,
    val data: AnalysisData? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AnalysisData(
    val analysisId: String,
    val imageName: String,
    val mean: Double,
    val stdDev: Double,
    val snr: Double,
    val variance: Double,
    val minValue: Int,
    val maxValue: Int,
    val quality: String,
    val diagnosis: String,
    val processingTimeMs: Long
)

@Serializable
data class ImageStorageResponse(
    val success: Boolean,
    val imageId: String,
    val storagePath: String,
    val storageSize: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AnalysisHistoryItem(
    val analysisId: String,
    val timestamp: Long,
    val imageName: String,
    val snr: Double,
    val quality: String,
    val storagePath: String
)

@Serializable
data class HistoryResponse(
    val success: Boolean,
    val items: List<AnalysisHistoryItem>,
    val totalCount: Int
)
