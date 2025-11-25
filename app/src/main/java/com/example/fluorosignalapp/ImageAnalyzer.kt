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

            return AnalysisResult(
                imageName = baseName,
                timestamp = System.currentTimeMillis(),
                mean = meanValue,
                stdDev = stdDevValue,
                snr = snrValue,
                variance = varianceValue,
                minPixelValue = minPixel,
                maxPixelValue = maxPixel,
                // Explicitly use default values for compatibility
                median = 0.0,
                skewness = 0.0,
                kurtosis = 0.0,
                validPixelCount = 0,
                redMean = null,
                redStdDev = null,
                redSnr = null,
                blueMean = null,
                blueStdDev = null,
                blueSnr = null,
                analysisRegion = null,
                warnings = emptyList(),
                // Placeholder values for custom fields
                quality = ImageQuality.GOOD, 
                diagnosis = "Diagnosis logic to be implemented."
            )

        } finally {
            matsToRelease.forEach { it.release() }
        }
    }
}
