package com.example.fluorosignalapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.pow

// 假设 ImageQuality 和 AnalysisResult 定义在其他文件中，此处省略
// enum class ImageQuality { GOOD, WARNING, BAD, UNKNOWN } 
// data class AnalysisResult(...)

/**
 * ImageAnalyzer 负责对荧光图像进行分析。
 * 核心改进：引入了 ROI 裁剪，确保统计数据只来自样品区域。
 */
class ImageAnalyzer {

    private val TAG = "ImageAnalyzer"

    // =========================================================================
    // ⚠️ ROI 配置
    // 现在的逻辑是：自动寻找画面中最亮的点作为中心，然后裁剪出以下大小的区域
    // =========================================================================
    private val ROI_WIDTH = 400  // 缩小 ROI 范围以更聚焦于试管 (原 800)
    private val ROI_HEIGHT = 400 // 缩小 ROI 范围以更聚焦于试管 (原 800)
    // =========================================================================

    /**
     * 分析图像文件，返回完整的多通道统计结果
     */
    suspend fun analyze(imageFile: File): AnalysisResult = withContext(Dispatchers.Default) {
        Log.i(TAG, "Starting analysis for: ${imageFile.name}")

        require(imageFile.exists()) {
            "Image file does not exist: ${imageFile.absolutePath}"
        }

        // 用于存储需要释放的 Mat 对象
        val matsToRelease = mutableListOf<Mat>()

        try {
            // 1. 加载图像
            val image = Imgcodecs.imread(imageFile.absolutePath).also {
                matsToRelease.add(it)
            }
            require(!image.empty()) {
                "Failed to load image: ${imageFile.absolutePath}"
            }

            // 2. 分离通道
            val channels = ArrayList<Mat>()
            Core.split(image, channels)
            require(channels.size == 3) {
                "Expected 3 channels (BGR), got ${channels.size}"
            }
            matsToRelease.addAll(channels)

            // 2.5. 【核心改进】 自动寻找最亮区域 (Auto-ROI)
            // 使用绿色通道寻找最亮斑点（通常荧光信号在绿色通道最强）
            val greenChannel = channels[1]
            val minMaxResult = Core.minMaxLoc(greenChannel)
            val maxLoc = minMaxResult.maxLoc // 最亮点的坐标 (x, y)
            val maxVal = minMaxResult.maxVal

            Log.i(TAG, "Found brightest spot at (${maxLoc.x}, ${maxLoc.y}) with value $maxVal")

            // 如果最大亮度太低，说明可能是一张全黑图片
            if (maxVal < 10) {
                Log.w(TAG, "Image is too dark (Max value < 10). Using center of image as fallback.")
                maxLoc.x = image.cols() / 2.0
                maxLoc.y = image.rows() / 2.0
            }

            // 计算 ROI 的左上角坐标，使其以最亮点为中心
            var roiX = (maxLoc.x - ROI_WIDTH / 2).toInt()
            var roiY = (maxLoc.y - ROI_HEIGHT / 2).toInt()

            // 边界修正：确保 ROI 不会超出图像边界
            if (roiX < 0) roiX = 0
            if (roiY < 0) roiY = 0
            if (roiX + ROI_WIDTH > image.cols()) roiX = image.cols() - ROI_WIDTH
            if (roiY + ROI_HEIGHT > image.rows()) roiY = image.rows() - ROI_HEIGHT

            val roiRect = Rect(roiX, roiY, ROI_WIDTH, ROI_HEIGHT)
            Log.i(TAG, "Dynamic ROI calculated: $roiRect")

            // 裁剪 ROI
            val blueROI = channels[0].submat(roiRect)
            val greenROI = channels[1].submat(roiRect)
            val redROI = channels[2].submat(roiRect)

            // 3. 计算各通道统计量（使用 ROI 区域）
            val greenStats = computeChannelStats(greenROI, "Green")
            val redStats = computeChannelStats(redROI, "Red")
            val blueStats = computeChannelStats(blueROI, "Blue")

            // 4. 检测质量问题（基于绿色通道 ROI 数据）
            val warnings = mutableListOf<String>()
            if (greenStats.maxPixel >= 250) {
                warnings.add("严重过曝：绿色通道最大值 ${greenStats.maxPixel}")
            }
            // 降低了低信号的阈值，因为暗室拍摄可能整体较暗
            if (greenStats.mean < 5) {
                warnings.add("信号过弱：绿色通道均值 ${greenStats.mean}")
            }
            
            // 5. 计算有效像素数（排除过饱和区域）
            val validPixelCount = countValidPixels(greenROI, threshold = 250)

            Log.i(TAG, "Analysis complete: SNR=${greenStats.snr}, Valid Pixels=$validPixelCount")

            return@withContext AnalysisResult(
                imageName = imageFile.nameWithoutExtension,
                timestamp = System.currentTimeMillis(),

                // 主要统计量（绿色通道）
                mean = greenStats.mean,
                stdDev = greenStats.stdDev,
                snr = greenStats.snr,
                variance = greenStats.variance,
                minPixelValue = greenStats.minPixel,
                maxPixelValue = greenStats.maxPixel,

                // 高级统计量
                median = greenStats.median,
                skewness = greenStats.skewness,
                kurtosis = greenStats.kurtosis,
                validPixelCount = validPixelCount,

                // 多通道数据
                redMean = redStats.mean,
                redStdDev = redStats.stdDev,
                redSnr = redStats.snr,

                blueMean = blueStats.mean,
                blueStdDev = blueStats.stdDev,
                blueSnr = blueStats.snr,

                analysisRegion = "Auto-ROI: (${roiX},${roiY}) ${ROI_WIDTH}x${ROI_HEIGHT}", // 更新 ROI 信息
                warnings = warnings,

                // 占位值（由 DiagnosisService 填充）
                quality = ImageQuality.UNKNOWN,
                diagnosis = ""
            )

        } finally {
            // 9. 内存管理：释放所有 Mat 对象
            matsToRelease.forEach { mat ->
                try {
                    mat.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to release Mat: ${e.message}")
                }
            }
            Log.d(TAG, "Released ${matsToRelease.size} Mat objects")
        }
    }

    /**
     * 计算单个通道的完整统计量
     */
    private fun computeChannelStats(channel: Mat, channelName: String): ChannelStats {
        val mean = MatOfDouble()
        val stdDev = MatOfDouble()
        Core.meanStdDev(channel, mean, stdDev)

        val meanValue = mean.get(0, 0)[0]
        val stdDevValue = stdDev.get(0, 0)[0]
        // 释放临时 MatOfDouble 对象
        mean.release()
        stdDev.release()

        val snrValue = if (stdDevValue != 0.0) meanValue / stdDevValue else Double.MAX_VALUE
        val varianceValue = stdDevValue * stdDevValue

        val minMaxLocResult = Core.minMaxLoc(channel)
        val minPixel = minMaxLocResult.minVal.toInt()
        val maxPixel = minMaxLocResult.maxVal.toInt()

        val medianValue = computeMedian(channel)

        val (skewnessValue, kurtosisValue) = computeMoments(channel, meanValue, stdDevValue)

        Log.d("ImageAnalyzer", "$channelName ROI: Mean=${String.format("%.2f", meanValue)}, SNR=${String.format("%.2f", snrValue)}")

        return ChannelStats(
            mean = meanValue,
            stdDev = stdDevValue,
            snr = snrValue,
            variance = varianceValue,
            minPixel = minPixel,
            maxPixel = maxPixel,
            median = medianValue,
            skewness = skewnessValue,
            kurtosis = kurtosisValue
        )
    }

    /**
     * 计算中位数（使用直方图方法）
     */
    private fun computeMedian(channel: Mat): Double {
        val histSize = 256
        val hist = Mat()
        Imgproc.calcHist(
            listOf(channel),
            org.opencv.core.MatOfInt(0),
            Mat(),
            hist,
            org.opencv.core.MatOfInt(histSize),
            org.opencv.core.MatOfFloat(0f, 256f)
        )

        val totalPixels = channel.rows() * channel.cols()
        val halfPixels = totalPixels / 2.0
        var cumulativeCount = 0.0

        for (i in 0 until histSize) {
            cumulativeCount += hist.get(i, 0)[0]
            if (cumulativeCount >= halfPixels) {
                hist.release()
                return i.toDouble()
            }
        }

        hist.release()
        return 0.0
    }

    /**
     * 计算偏度和峰度
     */
    private fun computeMoments(channel: Mat, mean: Double, stdDev: Double): Pair<Double, Double> {
        if (stdDev == 0.0) return Pair(0.0, 0.0)

        var moment3 = 0.0
        var moment4 = 0.0
        val totalPixels = channel.rows() * channel.cols()

        // 注意：计算矩可能非常耗时，但这是准确计算的必要步骤。
        for (row in 0 until channel.rows()) {
            for (col in 0 until channel.cols()) {
                // 读取像素值，假设通道是单通道灰度图
                val pixelValue = channel.get(row, col)[0]
                val deviation = pixelValue - mean
                moment3 += deviation.pow(3)
                moment4 += deviation.pow(4)
            }
        }

        // 偏度 (Skewness)
        val skewness = moment3 / (totalPixels * stdDev.pow(3))
        // 峰度 (Kurtosis), 减去 3 是为了计算“超额峰度” (Excess Kurtosis)
        val kurtosis = moment4 / (totalPixels * stdDev.pow(4)) - 3.0

        return Pair(skewness, kurtosis)
    }

    /**
     * 统计有效像素数（未过曝区域）
     */
    private fun countValidPixels(channel: Mat, threshold: Int = 250): Int {
        var count = 0
        // 注意：这里手动循环读取像素是为了避免创建额外的 Mat 对象
        for (row in 0 until channel.rows()) {
            for (col in 0 until channel.cols()) {
                if (channel.get(row, col)[0] < threshold) {
                    count++
                }
            }
        }
        return count
    }

    /**
     * 内部数据类：单通道统计结果
     */
    private data class ChannelStats(
        val mean: Double,
        val stdDev: Double,
        val snr: Double,
        val variance: Double,
        val minPixel: Int,
        val maxPixel: Int,
        val median: Double,
        val skewness: Double,
        val kurtosis: Double
    )
}
