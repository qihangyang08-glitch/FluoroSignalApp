package com.example.fluorosignalapp

import android.util.Log
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

class ImageAnalyzer {

    private val TAG = "ImageAnalyzer"

    suspend fun analyze(imageFile: File): AnalysisResult {
        Log.i(TAG, "Starting analysis for: ${imageFile.name}")

        if (!imageFile.exists()) {
            throw IllegalArgumentException("Image file does not exist: ${imageFile.absolutePath}")
        }

        val matsToRelease = mutableListOf<Mat>()

        try {
            val image = Imgcodecs.imread(imageFile.absolutePath)
            if (image.empty()) {
                throw IllegalArgumentException("Failed to load image: ${imageFile.absolutePath}")
            }
            matsToRelease.add(image)

            val channels = ArrayList<Mat>()
            Core.split(image, channels)

            if (channels.size != 3) {
                throw IllegalStateException("Expected 3 channels (BGR), got ${channels.size}")
            }
            matsToRelease.addAll(channels)

            val greenChannel = channels[1]
            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(greenChannel, mean, stdDev)

            val meanValue = mean.get(0, 0)[0]
            val stdDevValue = stdDev.get(0, 0)[0]
            matsToRelease.add(mean)
            matsToRelease.add(stdDev)

            val snrValue = if (stdDevValue != 0.0) meanValue / stdDevValue else Double.MAX_VALUE
            val varianceValue = stdDevValue * stdDevValue

            val minMaxLocResult = Core.minMaxLoc(greenChannel)
            val minPixel = minMaxLocResult.minVal.toInt()
            val maxPixel = minMaxLocResult.maxVal.toInt()

            val baseName = imageFile.name

            // 待办事项：请实现以下逻辑，根据计算出的指标确定最终的
            //“质量”和“诊断”字段。
            //
            //  - 初始化用于质量和诊断的可变变量：
            //    var quality = ImageQuality.GOOD
            //    var diagnosis = ""
            //
            //  - 按顺序应用以下规则。第一个匹配的规则决定结果。
            //
            //  1. 过曝检查 (BAD):
            //     - IF maxPixel > 250
            //     - THEN set quality = ImageQuality.BAD
            //     - AND set diagnosis = "图像过曝，信号可能饱和，结果不可靠。"
            //
            //  2. 曝光不足检查 (BAD):
            //     - ELSE IF mean < 10
            //     - THEN set quality = ImageQuality.BAD
            //     - AND set diagnosis = "图像欠曝，信号过弱，无法准确分析。"
            //
            //  3. 高噪音检查 (WARNING):
            //     - ELSE IF snr < 5.0
            //     - THEN set quality = ImageQuality.WARNING
            //     - AND set diagnosis = "信噪比过低，噪声可能影响结果准确性。"
            //
            //  4. 品质好 (GOOD):
            //     - ELSE (if no other rules match)
            //     - THEN set quality = ImageQuality.GOOD
            //     - AND set diagnosis = "图像质量良好，结果可信。"
            //

            return AnalysisResult(
                imageName = baseName,
                timestamp = System.currentTimeMillis(),
                mean = meanValue,
                stdDev = stdDevValue,
                snr = snrValue,
                variance = varianceValue,
                minPixelValue = minPixel,
                maxPixelValue = maxPixel,
                // Placeholder values, to be replaced by the logic above.
                quality = ImageQuality.GOOD, 
                diagnosis = "Diagnosis logic to be implemented."
            )

        } finally {
            matsToRelease.forEach { it.release() }
        }
    }
}
