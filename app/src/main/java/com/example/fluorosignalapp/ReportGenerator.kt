package com.example.fluorosignalapp

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object ReportGenerator {

    /**
     * Generates a CSV report from an AnalysisResult and saves it to the public Downloads directory.
     *
     * @param context The application context.
     * @param result The analysis result to export.
     * @return The Uri of the newly created CSV file.
     * @throws IOException if the file cannot be created or written to.
     */
    suspend fun exportResultAsCsv(context: Context, result: AnalysisResult): Uri = withContext(Dispatchers.IO) {
        // 1. Define CSV Content
        val header = "Image Name,Timestamp,Mean,Standard Deviation,SNR,Variance,Min Pixel,Max Pixel,Median,Skewness,Red Mean,Blue Mean,Quality,Diagnosis,Warnings"
        val dataRow = with(result) {
            // Ensure diagnosis string with commas is properly quoted
            val escapedDiagnosis = "\"${diagnosis.replace("\"", "\"\"")}\""
            val warningsString = "\"${warnings.joinToString(", ")}\""
            "$imageName,$timestamp,$mean,$stdDev,$snr,$variance,$minPixelValue,$maxPixelValue,$median,$skewness,${redMean ?: "N/A"},${blueMean ?: "N/A"},$quality,$escapedDiagnosis,$warningsString"
        }
        val csvContent = "$header\n$dataRow"

        // 2. Use MediaStore to Save the File
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Report_${result.imageName.substringBefore('.')}.csv")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.getContentUri("external"), contentValues)
            ?: throw IOException("Failed to create new MediaStore record for CSV.")

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(csvContent.toByteArray())
            } ?: throw IOException("Failed to get output stream for Uri: $uri")
        } catch (e: IOException) {
            // If there is an error, delete the incomplete MediaStore entry
            resolver.delete(uri, null, null)
            throw e
        }

        // 3. Return the Uri
        return@withContext uri
    }

    /**
     * Generates a PDF report from an AnalysisResult and saves it to the public Downloads directory.
     *
     * @param context The application context.
     * @param result The analysis result to export.
     * @return The Uri of the newly created PDF file.
     * @throws IOException if the file cannot be created or written to.
     */
    suspend fun exportResultAsPdf(context: Context, result: AnalysisResult): Uri = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size in points (approx)
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Title
        paint.color = Color.BLACK
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FluoroSignal Analysis Report", 50f, 50f, paint)

        // Timestamp
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Generated on: ${java.util.Date()}", 50f, 80f, paint)

        // Divider
        paint.strokeWidth = 1f
        canvas.drawLine(50f, 90f, 545f, 90f, paint)

        // Content
        var yPosition = 120f
        val lineHeight = 25f
        paint.textSize = 14f

        fun drawLine(label: String, value: String) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(label, 50f, yPosition, paint)
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(value, 200f, yPosition, paint)
            yPosition += lineHeight
        }

        drawLine("Image Name:", result.imageName)
        drawLine("Timestamp:", result.timestamp.toString())
        drawLine("Diagnosis:", result.diagnosis)
        drawLine("Quality:", result.quality.name)
        
        yPosition += 10f // Spacer

        drawLine("SNR:", String.format("%.2f", result.snr))
        drawLine("Mean:", String.format("%.2f", result.mean))
        drawLine("Std Dev:", String.format("%.2f", result.stdDev))
        drawLine("Variance:", String.format("%.2f", result.variance))
        drawLine("Min Pixel:", result.minPixelValue.toString())
        drawLine("Max Pixel:", result.maxPixelValue.toString())
        
        if (result.redMean != null) {
            drawLine("Red Mean:", String.format("%.2f", result.redMean))
        }
        if (result.blueMean != null) {
            drawLine("Blue Mean:", String.format("%.2f", result.blueMean))
        }

        if (result.warnings.isNotEmpty()) {
            yPosition += 20f
            paint.color = Color.RED
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Warnings:", 50f, yPosition, paint)
            yPosition += lineHeight
            paint.typeface = Typeface.DEFAULT
            result.warnings.forEach { warning ->
                canvas.drawText("- $warning", 50f, yPosition, paint)
                yPosition += lineHeight
            }
        }

        pdfDocument.finishPage(page)

        // Save to MediaStore
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Report_${result.imageName.substringBefore('.')}.pdf")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.getContentUri("external"), contentValues)
            ?: throw IOException("Failed to create new MediaStore record for PDF.")

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            } ?: throw IOException("Failed to get output stream for Uri: $uri")
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        } finally {
            pdfDocument.close()
        }

        return@withContext uri
    }
}
