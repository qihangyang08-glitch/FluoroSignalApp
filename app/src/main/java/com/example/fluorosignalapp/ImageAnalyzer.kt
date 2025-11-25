package com.example.fluorosignalapp

import android.util.Log
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt
import java.io.File

class ImageAnalyzer {

    private val TAG = "ImageAnalyzer"

    /**
     * 增强版分析方法，支持更多预处理和精细化统计
     */
    suspend fun analyze(
        imageFile: File,
        useGaussianBlur: Boolean = false,
        useMedianBlur: Boolean = false,
        ignoreExtreme: Boolean = false,
        extremeQuantile: Pair<Double, Double> = Pair(0.01, 0.99),
        normalize: Boolean = false,
        roiRect: Rect? = null,
        analyzeAllChannels: Boolean = true
    ): AnalysisResult {
        Log.i(TAG, "Starting enhanced analysis for: ${imageFile.name}")

        if (!imageFile.exists()) {
            throw IllegalArgumentException("Image file does not exist: ${imageFile.absolutePath}")
        }

        val matsToRelease = mutableListOf<Mat>()

        try {
            // 1. 加载图像
            var image = Imgcodecs.imread(imageFile.absolutePath)
            if (image.empty()) {
                throw IllegalArgumentException("Failed to load image: ${imageFile.absolutePath}")
            }
            matsToRelease.add(image)
            Log.d(TAG, "Image loaded: ${image.cols()}x${image.rows()} channels:${image.channels()} type:${image.type()}")

            // 2. 确保图像是 BGR 三通道格式
            if (image.channels() == 1) {
                val bgrImage = Mat()
                Imgproc.cvtColor(image, bgrImage, Imgproc.COLOR_GRAY2BGR)
                matsToRelease.add(bgrImage)
                image = bgrImage
                Log.d(TAG, "Converted grayscale to BGR")
            } else if (image.channels() == 4) {
                val bgrImage = Mat()
                Imgproc.cvtColor(image, bgrImage, Imgproc.COLOR_RGBA2BGR)
                matsToRelease.add(bgrImage)
                image = bgrImage
                Log.d(TAG, "Converted RGBA to BGR")
            }

            // 3. 确保数据类型是 CV_8U
            if (image.depth() != CvType.CV_8U) {
                val convertedImage = Mat()
                image.convertTo(convertedImage, CvType.CV_8U)
                matsToRelease.add(convertedImage)
                image = convertedImage
                Log.d(TAG, "Converted image depth to CV_8U")
            }

            // 4. 颜色通道分离
            val channels = ArrayList<Mat>()
            Core.split(image, channels)
            matsToRelease.addAll(channels)
            Log.d(TAG, "Channels split: ${channels.size}")

            // 5. 提取或裁剪为ROI
            var greenChannel = channels[1]
            var analysisWidth = greenChannel.cols()
            var analysisHeight = greenChannel.rows()

            if (roiRect != null && !roiRect.empty()) {
                try {
                    val roi = Mat(greenChannel, roiRect)
                    matsToRelease.add(roi)
                    greenChannel = roi
                    analysisWidth = roiRect.width
                    analysisHeight = roiRect.height
                    Log.d(TAG, "ROI applied: ${roiRect.x},${roiRect.y} [${roiRect.width}x${roiRect.height}]")
                } catch (e: Exception) {
                    Log.w(TAG, "ROI extraction failed: ${e.message}, using full image")
                }
            }

            // 6. 预处理链
            var processedChannel = greenChannel
            processedChannel = applyPreprocessing(
                processedChannel,
                useGaussianBlur,
                useMedianBlur,
                normalize,
                matsToRelease
            )

            // 7. 统计绿色通道
            val greenStats = calculateChannelStats(
                processedChannel,
                ignoreExtreme,
                extremeQuantile
            )
            Log.d(TAG, "Green channel - Mean: ${greenStats.mean}, StdDev: ${greenStats.stdDev}")

            // 8. 可选：分析其他通道
            var redMean: Double? = null
            var redStdDev: Double? = null
            var redSnr: Double? = null

            var blueMean: Double? = null
            var blueStdDev: Double? = null
            var blueSnr: Double? = null

            if (analyzeAllChannels) {
                try {
                    val red = channels[2].clone()
                    matsToRelease.add(red)
                    val redStats = calculateChannelStats(red, ignoreExtreme, extremeQuantile)
                    redMean = redStats.mean
                    redStdDev = redStats.stdDev
                    redSnr = redStats.snr

                    val blue = channels[0].clone()
                    matsToRelease.add(blue)
                    val blueStats = calculateChannelStats(blue, ignoreExtreme, extremeQuantile)
                    blueMean = blueStats.mean
                    blueStdDev = blueStats.stdDev
                    blueSnr = blueStats.snr

                    Log.d(TAG, "All channels analyzed")
                } catch (e: Exception) {
                    Log.w(TAG, "Error analyzing all channels: ${e.message}")
                }
            }

            // 9. 异常检测
            val warnings = detectAnomalies(greenStats, analysisWidth, analysisHeight)

            // 10. 组织结果
            val result = AnalysisResult(
                imageName = imageFile.nameWithoutExtension,
                timestamp = System.currentTimeMillis(),
                mean = greenStats.mean,
                stdDev = greenStats.stdDev,
                snr = greenStats.snr,
                variance = greenStats.variance,
                minPixelValue = greenStats.minPixel,
                maxPixelValue = greenStats.maxPixel,
                median = greenStats.median,
                skewness = greenStats.skewness,
                kurtosis = greenStats.kurtosis,
                validPixelCount = greenStats.validPixelCount,
                redMean = redMean,
                redStdDev = redStdDev,
                redSnr = redSnr,
                blueMean = blueMean,
                blueStdDev = blueStdDev,
                blueSnr = blueSnr,
                analysisRegion = roiRect?.toString(),
                warnings = warnings,
                quality = ImageQuality.UNKNOWN,
                diagnosis = "",
                isSaturated = greenStats.maxPixel >= 254
            )

            Log.i(TAG, "Analysis completed successfully. SNR: ${result.snr}, Warnings: ${warnings.size}")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "Error during analysis: ${e.message}", e)
            throw e
        } finally {
            matsToRelease.forEach {
                try {
                    if (!it.empty()) {
                        it.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing Mat: ${e.message}")
                }
            }
            Log.d(TAG, "Released ${matsToRelease.size} Mat objects")
        }
    }

    private fun applyPreprocessing(
        channel: Mat,
        useGaussian: Boolean,
        useMedian: Boolean,
        normalize: Boolean,
        matsToRelease: MutableList<Mat>
    ): Mat {
        var result = channel

        if (useGaussian) {
            try {
                val blurred = Mat()
                Imgproc.GaussianBlur(result, blurred, Size(3.0, 3.0), 0.0)
                matsToRelease.add(blurred)
                result = blurred
                Log.d(TAG, "Gaussian blur applied")
            } catch (e: Exception) {
                Log.w(TAG, "Error applying Gaussian blur: ${e.message}")
            }
        }

        if (useMedian) {
            try {
                val median = Mat()
                Imgproc.medianBlur(result, median, 3)
                matsToRelease.add(median)
                result = median
                Log.d(TAG, "Median blur applied")
            } catch (e: Exception) {
                Log.w(TAG, "Error applying median blur: ${e.message}")
            }
        }

        if (normalize) {
            try {
                val norm = Mat()
                Core.normalize(result, norm, 0.0, 255.0, Core.NORM_MINMAX)
                matsToRelease.add(norm)
                result = norm
                Log.d(TAG, "Normalization applied")
            } catch (e: Exception) {
                Log.w(TAG, "Error applying normalization: ${e.message}")
            }
        }

        return result
    }

    private fun calculateChannelStats(
        channel: Mat,
        ignoreExtreme: Boolean,
        quantile: Pair<Double, Double>
    ): ChannelStatistics {
        var workingChannel = channel
        val matsToClean = mutableListOf<Mat>()

        try {
            if (channel.channels() > 1) {
                val gray = Mat()
                Imgproc.cvtColor(channel, gray, Imgproc.COLOR_BGR2GRAY)
                matsToClean.add(gray)
                workingChannel = gray
            }

            if (workingChannel.depth() != CvType.CV_8U) {
                val converted = Mat()
                workingChannel.convertTo(converted, CvType.CV_8U)
                matsToClean.add(converted)
                workingChannel = converted
            }

            val buf = ByteArray(workingChannel.total().toInt())
            workingChannel.get(0, 0, buf)
            
            val pixelData = if (ignoreExtreme) {
                val doubleArray = buf.map { (it.toInt() and 0xFF).toDouble() }.toDoubleArray()
                filterByQuantile(doubleArray, quantile.first, quantile.second)
            } else {
                buf.map { (it.toInt() and 0xFF).toDouble() }
            }

            if (pixelData.isEmpty()) {
                throw IllegalStateException("No valid pixels after filtering")
            }

            val mean = pixelData.average()
            val variance = pixelData.map { (it - mean).let { d -> d * d } }.average()
            val stdDev = sqrt(variance)
            val snr = if (stdDev != 0.0) mean / stdDev else Double.MAX_VALUE

            val minMaxLoc = Core.minMaxLoc(workingChannel)
            val minPixel = minMaxLoc.minVal.toInt()
            val maxPixel = minMaxLoc.maxVal.toInt()

            val sorted = pixelData.sorted()
            val median = if (sorted.size % 2 == 0) {
                (sorted[sorted.size / 2] + sorted[sorted.size / 2 - 1]) / 2
            } else {
                sorted[sorted.size / 2]
            }

            val skewness = calculateSkewness(pixelData, mean, stdDev)
            val kurtosis = calculateKurtosis(pixelData, mean, stdDev)

            return ChannelStatistics(
                mean = mean,
                stdDev = stdDev,
                variance = variance,
                snr = snr,
                minPixel = minPixel,
                maxPixel = maxPixel,
                median = median,
                skewness = skewness,
                kurtosis = kurtosis,
                validPixelCount = pixelData.size
            )

        } finally {
            matsToClean.forEach {
                try {
                    if (!it.empty()) {
                        it.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing Mat: ${e.message}")
                }
            }
        }
    }

    private fun filterByQuantile(
        data: DoubleArray,
        lowQuantile: Double,
        highQuantile: Double
    ): List<Double> {
        val sorted = data.sorted()
        val n = sorted.size
        val lowIdx = (lowQuantile * n).toInt().coerceAtLeast(0)
        val highIdx = (highQuantile * n).toInt().coerceAtMost(n)
        return sorted.subList(lowIdx, highIdx)
    }

    private fun calculateSkewness(data: List<Double>, mean: Double, stdDev: Double): Double {
        if (stdDev == 0.0 || data.isEmpty()) return 0.0
        val m3 = data.map { (it - mean).let { d -> d * d * d } }.average()
        return m3 / (stdDev * stdDev * stdDev)
    }

    private fun calculateKurtosis(data: List<Double>, mean: Double, stdDev: Double): Double {
        if (stdDev == 0.0 || data.isEmpty()) return 0.0
        val m4 = data.map { (it - mean).let { d -> d * d * d * d } }.average()
        return m4 / (stdDev * stdDev * stdDev * stdDev) - 3.0
    }

    private fun detectAnomalies(
        stats: ChannelStatistics,
        width: Int,
        height: Int
    ): List<String> {
        val warnings = mutableListOf<String>()

        if (stats.mean < 20.0) {
            warnings.add("⚠️ 图像过暗 (mean=${String.format("%.2f", stats.mean)})")
        }
        if (stats.mean > 235.0) {
            warnings.add("⚠️ 图像过亮 (mean=${String.format("%.2f", stats.mean)})")
        }

        if (stats.snr < 2.0) {
            warnings.add("⚠️ 信噪比低 (SNR=${String.format("%.2f", stats.snr)})")
        }

        if (stats.stdDev < 5.0) {
            warnings.add("⚠️ 方差过小，可能是噪声图像")
        }

        if (kotlin.math.abs(stats.skewness) > 2.0) {
            warnings.add("⚠️ 分布严重不对称 (skewness=${String.format("%.4f", stats.skewness)})")
        }

        if (width > 0 && height > 0) {
            val totalPixels = width * height
            val validRatio = stats.validPixelCount.toDouble() / totalPixels
            if (validRatio < 0.5) {
                warnings.add("⚠️ 有效像素比例低 (${(validRatio * 100).toInt()}%)")
            }
        }

        return warnings
    }

    data class ChannelStatistics(
        val mean: Double,
        val stdDev: Double,
        val variance: Double,
        val snr: Double,
        val minPixel: Int,
        val maxPixel: Int,
        val median: Double,
        val skewness: Double,
        val kurtosis: Double,
        val validPixelCount: Int
    )
}
