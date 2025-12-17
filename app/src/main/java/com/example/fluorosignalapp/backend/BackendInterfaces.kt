package com.example.fluorosignalapp.backend

import com.example.fluorosignalapp.AnalysisResult
import java.io.File

/**
 * 后端服务接口 - 定义业务逻辑层
 * 这层负责核心业务逻辑，与具体实现解耦
 */

interface IAnalysisBackendService {
    /**
     * 执行图像分析
     * @param imageFile 要分析的图像文件
     * @return 分析结果
     */
    suspend fun analyzeImage(imageFile: File): AnalysisResult

    /**
     * 批量分析
     * @param imageFiles 多个图像文件
     * @return 分析结果列表
     */
    suspend fun analyzeImages(imageFiles: List<File>): List<AnalysisResult>

    /**
     * 获取分析进度
     * @return 0-100的进度值
     */
    fun getProgress(): Int
}

interface IImageStorageBackendService {
    /**
     * 保存图像到服务器
     * @param imageName 图像名称
     * @param imageFile 图像文件
     * @return 保存路径
     */
    suspend fun saveImage(imageName: String, imageFile: File): String

    /**
     * 获取已保存的图像
     * @param imageId 图像ID
     * @return 图像文件
     */
    suspend fun getImage(imageId: String): File?

    /**
     * 删除保存的图像
     * @param imageId 图像ID
     * @return 是否删除成功
     */
    suspend fun deleteImage(imageId: String): Boolean

    /**
     * 获取存储统计信息
     * @return 已用空间(字节)
     */
    fun getStorageStats(): Long
}

interface IAnalysisDatabaseService {
    /**
     * 保存分析记录
     * @param result 分析结果
     * @return 记录ID
     */
    suspend fun saveAnalysis(result: AnalysisResult): String

    /**
     * 获取分析历史
     * @param limit 返回最多条数
     * @param offset 分页偏移
     * @return 分析结果列表
     */
    suspend fun getAnalysisHistory(limit: Int = 20, offset: Int = 0): List<AnalysisResult>

    /**
     * 按ID获取分析结果
     * @param analysisId 分析ID
     * @return 分析结果
     */
    suspend fun getAnalysis(analysisId: String): AnalysisResult?

    /**
     * 删除分析记录
     * @param analysisId 分析ID
     * @return 是否删除成功
     */
    suspend fun deleteAnalysis(analysisId: String): Boolean

    /**
     * 清空所有记录
     */
    suspend fun clearAll()

    /**
     * 获取记录总数
     */
    suspend fun getTotalCount(): Int
}

interface IBackendApiServer {
    /**
     * 启动后端服务器
     * @param port 监听端口
     */
    suspend fun start(port: Int = 8080)

    /**
     * 停止后端服务器
     */
    suspend fun stop()

    /**
     * 检查服务器是否运行
     */
    fun isRunning(): Boolean

    /**
     * 获取服务器地址
     */
    fun getServerUrl(): String
}

interface IReportGenerationService {
    /**
     * 生成PDF报告
     * @param result 分析结果
     * @return PDF文件路径
     */
    suspend fun generatePdfReport(result: AnalysisResult): String

    /**
     * 生成CSV数据
     * @param results 多个分析结果
     * @return CSV文件路径
     */
    suspend fun generateCsvReport(results: List<AnalysisResult>): String

    /**
     * 生成JSON数据
     * @param result 分析结果
     * @return JSON字符串
     */
    suspend fun generateJsonReport(result: AnalysisResult): String
}
