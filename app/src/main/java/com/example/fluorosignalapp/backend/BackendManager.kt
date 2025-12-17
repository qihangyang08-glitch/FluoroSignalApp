package com.example.fluorosignalapp.backend

import android.content.Context
import android.util.Log
import com.example.fluorosignalapp.AnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 后端管理器 - 统一的后端服务入口
 * 前端只需要与此管理器通信，无需直接调用各个服务
 * 采用Facade模式简化前端的使用
 */
class BackendManager private constructor(private val context: Context) {

    private val TAG = "BackendManager"

    // 各个后端服务
    private val analysisService = AnalysisBackendService()
    private val storageService = ImageStorageBackendService(context)
    private val databaseService = AnalysisDatabaseService(context)

    // 状态流 - 前端监听这些状态
    private val _analysisProgress = MutableStateFlow(0)
    val analysisProgress: StateFlow<Int> = _analysisProgress.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    private val _analysisResult = MutableStateFlow<AnalysisResult?>(null)
    val analysisResult: StateFlow<AnalysisResult?> = _analysisResult.asStateFlow()

    /**
     * 核心功能：执行图像分析
     * 这是前端最常用的方法
     */
    suspend fun performAnalysis(imageFile: File): AnalysisResult? {
        return try {
            _isAnalyzing.value = true
            _analysisError.value = null

            Log.i(TAG, "Starting analysis: ${imageFile.name}")

            // 1. 后端分析
            val result = analysisService.analyzeImage(imageFile)

            // 2. 保存到数据库
            val recordId = databaseService.saveAnalysis(result)
            Log.d(TAG, "Analysis saved with ID: $recordId")

            // 3. 保存图像到存储
            val imageId = storageService.saveImage(imageFile.name, imageFile)
            Log.d(TAG, "Image saved with ID: $imageId")

            // 4. 返回结果
            _analysisResult.value = result
            _isAnalyzing.value = false

            Log.i(TAG, "Analysis complete: SNR=${result.snr}, Quality=${result.quality}")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed: ${e.message}", e)
            _analysisError.value = "分析失败: ${e.message}"
            _isAnalyzing.value = false
            null
        }
    }

    /**
     * 获取分析历史
     */
    suspend fun getAnalysisHistory(limit: Int = 20, offset: Int = 0): List<AnalysisResult> {
        return try {
            databaseService.getAnalysisHistory(limit, offset)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get history: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 获取单个分析结果
     */
    suspend fun getAnalysisById(id: String): AnalysisResult? {
        return try {
            databaseService.getAnalysis(id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get analysis: ${e.message}", e)
            null
        }
    }

    /**
     * 删除分析记录
     */
    suspend fun deleteAnalysis(id: String): Boolean {
        return try {
            databaseService.deleteAnalysis(id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete analysis: ${e.message}", e)
            false
        }
    }

    /**
     * 获取数据库统计信息
     */
    suspend fun getDatabaseStats(): AnalysisDatabaseService.DatabaseStatistics {
        return try {
            databaseService.getStatistics()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get stats: ${e.message}", e)
            AnalysisDatabaseService.DatabaseStatistics(0, 0.0, 0, 0L, 0L)
        }
    }

    /**
     * 获取存储统计信息
     */
    fun getStorageStats(): Long {
        return storageService.getStorageStats()
    }

    /**
     * 清理过期数据
     */
    suspend fun cleanup(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        try {
            storageService.cleanup(maxAgeMs)
            Log.i(TAG, "Cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed: ${e.message}", e)
        }
    }

    /**
     * 清空所有数据
     */
    suspend fun clearAll() {
        try {
            databaseService.clearAll()
            Log.i(TAG, "Cleared all data")
        } catch (e: Exception) {
            Log.e(TAG, "Clear all failed: ${e.message}", e)
        }
    }

    /**
     * 获取当前分析进度
     */
    fun getAnalysisProgress(): Int {
        return analysisService.getProgress()
    }

    companion object {
        private var instance: BackendManager? = null

        /**
         * 获取单例实例
         */
        fun getInstance(context: Context): BackendManager {
            return instance ?: synchronized(this) {
                BackendManager(context).also { instance = it }
            }
        }

        /**
         * 初始化管理器
         */
        fun initialize(context: Context) {
            if (instance == null) {
                instance = BackendManager(context)
                Log.i("BackendManager", "Backend manager initialized")
            }
        }
    }
}
