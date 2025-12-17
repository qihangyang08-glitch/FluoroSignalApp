package com.example.fluorosignalapp.backend

import android.util.Log
import com.example.fluorosignalapp.AnalysisResult
import com.example.fluorosignalapp.ImageAnalyzer
import com.example.fluorosignalapp.data.diagnosis.DiagnosisService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 后端分析服务 - 将分析逻辑从UI层解耦
 * 实现IAnalysisBackendService接口
 */
class AnalysisBackendService : IAnalysisBackendService {

    private val TAG = "AnalysisBackend"
    private val imageAnalyzer = ImageAnalyzer()
    private var currentProgress = 0

    /**
     * 单个图像分析 - 后端处理逻辑
     * 在后端线程中执行，不阻塞UI
     */
    override suspend fun analyzeImage(imageFile: File): AnalysisResult {
        return withContext(Dispatchers.Default) {
            Log.i(TAG, "Backend: Starting analysis for ${imageFile.name}")
            currentProgress = 0

            try {
                // 第1步：执行图像分析
                currentProgress = 30
                val rawResult = imageAnalyzer.analyze(imageFile)
                Log.d(TAG, "Backend: Image analysis complete, SNR=${rawResult.snr}")

                // 第2步：执行诊断
                currentProgress = 60
                val diagnosedResult = DiagnosisService.diagnose(rawResult)
                Log.d(TAG, "Backend: Diagnosis complete, Quality=${diagnosedResult.quality}")

                // 第3步：返回最终结果
                currentProgress = 100
                Log.i(TAG, "Backend: Analysis complete for ${imageFile.name}")

                diagnosedResult

            } catch (e: Exception) {
                Log.e(TAG, "Backend analysis error: ${e.message}", e)
                currentProgress = 0
                throw e
            }
        }
    }

    /**
     * 批量分析 - 后端处理多个图像
     */
    override suspend fun analyzeImages(imageFiles: List<File>): List<AnalysisResult> {
        return withContext(Dispatchers.Default) {
            Log.i(TAG, "Backend: Starting batch analysis of ${imageFiles.size} images")

            val results = mutableListOf<AnalysisResult>()
            val totalFiles = imageFiles.size

            imageFiles.forEachIndexed { index, file ->
                try {
                    // 更新进度
                    currentProgress = (index * 100) / totalFiles

                    // 分析单个文件
                    val result = analyzeImage(file)
                    results.add(result)

                    Log.d(TAG, "Backend: Analyzed ${index + 1}/$totalFiles - ${file.name}")

                } catch (e: Exception) {
                    Log.e(TAG, "Backend: Failed to analyze ${file.name}: ${e.message}")
                    // 继续处理其他文件
                }
            }

            currentProgress = 100
            Log.i(TAG, "Backend: Batch analysis complete - ${results.size}/$totalFiles succeeded")

            results
        }
    }

    override fun getProgress(): Int = currentProgress

    /**
     * 后端分析配置 - 用于调整分析参数
     */
    data class AnalysisConfig(
        val roiX: Int = 500,
        val roiY: Int = 500,
        val roiWidth: Int = 800,
        val roiHeight: Int = 800,
        val enableAdvancedStats: Boolean = true,
        val enableQualityCheck: Boolean = true
    )

    companion object {
        private var instance: AnalysisBackendService? = null

        fun getInstance(): AnalysisBackendService {
            return instance ?: AnalysisBackendService().also { instance = it }
        }
    }
}
