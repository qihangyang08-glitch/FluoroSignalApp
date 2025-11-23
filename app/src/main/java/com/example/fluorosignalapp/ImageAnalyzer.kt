package com.example.fluorosignalapp

import android.util.Log
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

/**
 * ImageAnalyzer 负责对荧光图像进行分析。
 *
 * 使用 OpenCV 库对绿色荧光通道进行统计分析，
 * 计算均值、标准差、信噪比等关键指标。
 */
class ImageAnalyzer {

    private val TAG = "ImageAnalyzer"

    /**
     * 分析给定的图像文件，返回分析结果。
     *
     * 实现步骤：
     * 1. 使用 OpenCV 加载图像
     * 2. 分离 BGR 颜色通道
     * 3. 提取绿色通道
     * 4. 计算统计指标（均值、标准差）
     * 5. 计算派生指标（SNR、方差）
     * 6. 计算最小/最大像素值
     * 7. 释放所有 Mat 对象
     *
     * @param imageFile 要分析的图像文件
     * @return AnalysisResult 包含各项分析指标的结果对象
     * @throws Exception 如果文件不存在或分析过程出错
     */
    suspend fun analyze(imageFile: File): AnalysisResult {
        Log.i(TAG, "Starting analysis for: ${imageFile.name}")

        // 验证文件存在
        if (!imageFile.exists()) {
            throw IllegalArgumentException("Image file does not exist: ${imageFile.absolutePath}")
        }

        // 用于存储需要释放的 Mat 对象
        val matsToRelease = mutableListOf<Mat>()

        try {
            // 1. 加载图像
            val image = Imgcodecs.imread(imageFile.absolutePath)
            if (image.empty()) {
                throw IllegalArgumentException("Failed to load image: ${imageFile.absolutePath}")
            }
            matsToRelease.add(image)
            Log.d(TAG, "Image loaded successfully: ${image.cols()}x${image.rows()}")

            // 2. 颜色通道分离
            val channels = ArrayList<Mat>()
            Core.split(image, channels)

            if (channels.size != 3) {
                throw IllegalStateException("Expected 3 channels (BGR), got ${channels.size}")
            }

            // OpenCV 使用 BGR 顺序，所以：
            // channels[0] = Blue
            // channels[1] = Green
            // channels[2] = Red
            matsToRelease.addAll(channels)

            // 3. 选取绿色通道
            val greenChannel = channels[1]
            Log.d(TAG, "Green channel extracted")

            // 4. 计算核心指标（均值和标准差）
            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(greenChannel, mean, stdDev)

            val meanValue = mean.get(0, 0)[0]
            val stdDevValue = stdDev.get(0, 0)[0]

            matsToRelease.add(mean)
            matsToRelease.add(stdDev)

            Log.d(TAG, "Mean: $meanValue, StdDev: $stdDevValue")

            // 5. 计算派生指标
            val snrValue = if (stdDevValue != 0.0) {
                meanValue / stdDevValue
            } else {
                Double.MAX_VALUE // 如果标准差为0，SNR理论上无限大
            }
            val varianceValue = stdDevValue * stdDevValue

            // 6. 计算最小/最大像素值
            val minMaxLocResult = Core.minMaxLoc(greenChannel)
            val minPixel = minMaxLocResult.minVal.toInt()
            val maxPixel = minMaxLocResult.maxVal.toInt()

            Log.d(TAG, "Min pixel: $minPixel, Max pixel: $maxPixel")

            // 7. 从文件名中提取基础名（去除扩展名）
            val baseName = imageFile.nameWithoutExtension
            Log.d(TAG, "Extracted base name: $baseName")

            // 8. 构建并返回结果对象
            val result = AnalysisResult(
                imageName = baseName,
                timestamp = System.currentTimeMillis(),
                mean = meanValue,
                stdDev = stdDevValue,
                snr = snrValue,
                variance = varianceValue,
                minPixelValue = minPixel,
                maxPixelValue = maxPixel
            )

            Log.i(TAG, "Analysis completed for: ${imageFile.name}, SNR: ${result.snr}")

            return result

        } finally {
            // 9. 内存管理：释放所有 Mat 对象
            matsToRelease.forEach { mat ->
                try {
                    mat.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing Mat: ${e.message}")
                }
            }
            Log.d(TAG, "Released ${matsToRelease.size} Mat objects")
        }
    }
}
