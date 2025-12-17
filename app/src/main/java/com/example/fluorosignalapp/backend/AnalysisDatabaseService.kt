package com.example.fluorosignalapp.backend

import android.content.Context
import android.util.Log
import com.example.fluorosignalapp.AnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

/**
 * 后端数据库服务 - 管理分析历史记录
 * 实现IAnalysisDatabaseService接口
 * 使用JSON文件作为简单的本地数据库
 */
class AnalysisDatabaseService(private val context: Context) : IAnalysisDatabaseService {

    private val TAG = "DatabaseBackend"
    private val dbDir: File = File(context.cacheDir, "backend_database").apply {
        if (!exists()) mkdirs()
    }
    private val historyFile: File = File(dbDir, "analysis_history.json")
    private val json = Json { prettyPrint = true }

    private val analysisRecords = mutableListOf<AnalysisRecord>()

    init {
        // 初始化时加载已有记录
        loadFromDisk()
    }

    /**
     * 保存分析记录
     */
    override suspend fun saveAnalysis(result: AnalysisResult): String {
        return withContext(Dispatchers.IO) {
            try {
                val recordId = generateRecordId()
                val record = AnalysisRecord(
                    id = recordId,
                    result = result,
                    timestamp = System.currentTimeMillis(),
                    storagePath = ""
                )

                analysisRecords.add(record)
                saveToDisk()

                Log.i(TAG, "Saved analysis record: $recordId")
                recordId

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save analysis: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * 获取分析历史
     */
    override suspend fun getAnalysisHistory(limit: Int, offset: Int): List<AnalysisResult> {
        return withContext(Dispatchers.IO) {
            try {
                val sorted = analysisRecords.sortedByDescending { it.timestamp }
                val result = sorted
                    .drop(offset)
                    .take(limit)
                    .map { it.result }

                Log.d(TAG, "Retrieved ${result.size} analysis records (offset=$offset, limit=$limit)")
                result

            } catch (e: Exception) {
                Log.e(TAG, "Failed to get history: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * 按ID获取分析结果
     */
    override suspend fun getAnalysis(analysisId: String): AnalysisResult? {
        return withContext(Dispatchers.IO) {
            try {
                val record = analysisRecords.find { it.id == analysisId }
                Log.d(TAG, "Retrieved analysis: $analysisId - ${record?.result?.imageName}")
                record?.result

            } catch (e: Exception) {
                Log.e(TAG, "Failed to get analysis: ${e.message}", e)
                null
            }
        }
    }

    /**
     * 删除分析记录
     * 支持通过 Record ID 或 Image Name 删除
     */
    override suspend fun deleteAnalysis(analysisId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 尝试匹配 ID 或 ImageName
                val removed = analysisRecords.removeIf { 
                    it.id == analysisId || it.result.imageName == analysisId 
                }
                
                if (removed) {
                    saveToDisk()
                    Log.i(TAG, "Deleted analysis: $analysisId")
                } else {
                    Log.w(TAG, "Analysis not found for deletion: $analysisId")
                }
                removed

            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete analysis: ${e.message}", e)
                false
            }
        }
    }

    /**
     * 清空所有记录
     */
    override suspend fun clearAll() {
        return withContext(Dispatchers.IO) {
            try {
                analysisRecords.clear()
                saveToDisk()
                Log.i(TAG, "Cleared all analysis records")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear all: ${e.message}", e)
            }
        }
    }

    /**
     * 获取记录总数
     */
    override suspend fun getTotalCount(): Int {
        return withContext(Dispatchers.IO) {
            analysisRecords.size
        }
    }

    /**
     * 获取统计信息
     */
    suspend fun getStatistics(): DatabaseStatistics {
        return withContext(Dispatchers.IO) {
            val avgSnr = if (analysisRecords.isNotEmpty()) {
                analysisRecords.map { it.result.snr }.average()
            } else 0.0

            val goodQualityCount = analysisRecords.count {
                it.result.quality.name == "GOOD"
            }

            DatabaseStatistics(
                totalRecords = analysisRecords.size,
                averageSnr = avgSnr,
                goodQualityCount = goodQualityCount,
                oldestRecord = analysisRecords.minByOrNull { it.timestamp }?.timestamp ?: 0L,
                newestRecord = analysisRecords.maxByOrNull { it.timestamp }?.timestamp ?: 0L
            )
        }
    }

    private fun saveToDisk() {
        try {
            val jsonString = json.encodeToString(analysisRecords)
            historyFile.writeText(jsonString)
            Log.d(TAG, "Saved ${analysisRecords.size} records to disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to disk: ${e.message}", e)
        }
    }

    private fun loadFromDisk() {
        try {
            if (historyFile.exists()) {
                val jsonString = historyFile.readText()
                val loaded = json.decodeFromString<List<AnalysisRecord>>(jsonString)
                analysisRecords.addAll(loaded)
                Log.i(TAG, "Loaded ${loaded.size} records from disk")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load from disk: ${e.message}", e)
            analysisRecords.clear()
        }
    }

    private fun generateRecordId(): String {
        return "REC_${UUID.randomUUID().toString().take(12).uppercase()}"
    }

    /**
     * 分析记录数据类 - 序列化存储
     */
    @kotlinx.serialization.Serializable
    data class AnalysisRecord(
        val id: String,
        val result: AnalysisResult,
        val timestamp: Long,
        val storagePath: String
    )

    /**
     * 数据库统计信息
     */
    data class DatabaseStatistics(
        val totalRecords: Int,
        val averageSnr: Double,
        val goodQualityCount: Int,
        val oldestRecord: Long,
        val newestRecord: Long
    )

    companion object {
        private var instance: AnalysisDatabaseService? = null

        fun getInstance(context: Context): AnalysisDatabaseService {
            return instance ?: AnalysisDatabaseService(context).also { instance = it }
        }
    }
}
