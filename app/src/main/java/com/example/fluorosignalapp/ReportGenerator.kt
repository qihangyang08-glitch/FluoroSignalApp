package com.example.fluorosignalapp

import android.content.ContentValues
import android.content.Context
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
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
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
}
