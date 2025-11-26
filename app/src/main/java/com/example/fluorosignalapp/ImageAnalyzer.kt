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
    // ⚠️ WARNING: ROI 坐标 - 请根据实际拍摄和样品位置调整
    // 必须确保这个矩形精确覆盖您的样品分析区域！
    // -------------------------------------------------------------------------
    private val ROI_X = 500  // 裁剪区域起始 X 坐标
    private val ROI_Y = 500  // 裁剪区域起始 Y 坐标
    private val ROI_WIDTH = 800 // 裁剪区域宽度
    private val ROI_HEIGHT = 800 // 裁剪区域高度
    // =========================================================================

    /**
     * 分析图像文件，返回完整的多通道统计结果
     */
    suspend fun analyze(imageFile: File): AnalysisResult = withContext(Dispatchers.Default) {
        Log.i(TAG, "Starting analysis for: ${imageFile.name}. Using ROI: ${ROI_WIDTH}x${ROI_HEIGHT}")

        require(imageFile.exists()) {
            "Image file does not exist: ${imageFile.absolutePath}"
        }

        // 用于存储需要释放的 Mat 对象（主要是原始图像和通道分离的结果）
        val matsToRelease = mutableListOf<Mat>()

        try {
            // 1. 加载图像
            val image = Imgcodecs.imread(imageFile.absolutePath).also {
                matsToRelease.add(it)
            }
            require(!image.empty()) {
                "Failed to load image: ${imageFile.absolutePath}"
            }

            // 检查 ROI 是否越界
            val roiRect = Rect(ROI_X, ROI_Y, ROI_WIDTH, ROI_HEIGHT)
            require(roiRect.x + roiRect.width <= image.cols() && roiRect.y + roiRect.height <= image.rows()) {
                "ROI coordinates are out of bounds of the image (Image: ${image.cols()}x${image.rows()}, ROI: ${roiRect.x}, ${roiRect.y}, ${roiRect.width}, ${roiRect.height})"
            }

            // 2. 分离通道
            val channels = ArrayList<Mat>()
            Core.split(image, channels)
            require(channels.size == 3) {
                "Expected 3 channels (BGR), got ${channels.size}"
            }
            matsToRelease.addAll(channels)

            // 2.5. 【新增】 ROI 裁剪
            // submat() 返回的 Mat 只是原始 Mat 的头信息，不需要单独释放，因为原始 channels 会被释放
            val blueROI = channels[0].submat(roiRect)
            val greenROI = channels[1].submat(roiRect)
            val redROI = channels[2].submat(roiRect)

            Log.d(TAG, "ROI cropped successfully. New analysis size: ${greenROI.cols()}x${greenROI.rows()}")

            // 3. 计算各通道统计量（使用 ROI 区域）
            val greenStats = computeChannelStats(greenROI, "Green")
            val redStats = computeChannelStats(redROI, "Red")
            val blueStats = computeChannelStats(blueROI, "Blue")

            // 4. 检测质量问题（基于绿色通道 ROI 数据）
            val warnings = mutableListOf<String>()
            if (greenStats.maxPixel >= 250) {
                warnings.add("严重过曝：绿色通道最大值 ${greenStats.maxPixel}")
            }
            if (greenStats.mean < 10) {
                warnings.add("信号过弱：绿色通道均值 ${greenStats.mean}")
            }
            // 5.0 是一个经验值，可能需要调整
            if (greenStats.snr < 5.0 && greenStats.snr != Double.MAX_VALUE) { 
                warnings.add("信噪比偏低：SNR = ${String.format("%.2f", greenStats.snr)}")
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

                analysisRegion = "ROI: (${ROI_X},${ROI_Y}) ${ROI_WIDTH}x${ROI_HEIGHT}", // 更新 ROI 信息
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
