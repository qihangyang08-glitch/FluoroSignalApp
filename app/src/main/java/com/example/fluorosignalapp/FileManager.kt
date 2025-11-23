package com.example.fluorosignalapp

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object FileManager {

    private const val TAG = "FileManager"
    private const val ROOT_FOLDER_NAME = "FluoroAppData"
    private const val PENDING_FOLDER_NAME = "PendingAnalysis"
    private const val HISTORY_FOLDER_NAME = "AnalysisHistory"

    private lateinit var rootDirectory: File
    private lateinit var pendingDir: File
    private lateinit var historyDir: File

    fun initialize(context: Context) {
        if (this::rootDirectory.isInitialized) {
            return
        }
        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: throw IllegalStateException("Cannot access external files directory")

        rootDirectory = File(documentsDir, ROOT_FOLDER_NAME)
        pendingDir = File(rootDirectory, PENDING_FOLDER_NAME)
        historyDir = File(rootDirectory, HISTORY_FOLDER_NAME)

        createDirectoryIfNeeded(rootDirectory, "Root")
        createDirectoryIfNeeded(pendingDir, "Pending")
        createDirectoryIfNeeded(historyDir, "History")
        Log.i(TAG, "FileManager initialized successfully")
    }

    suspend fun saveImageToPending(imageBytes: ByteArray): File = withContext(Dispatchers.IO) {
        checkInitialized()
        val baseName = generateUniqueFileNameBase()
        // Corrected: Save as .png
        val imageFile = File(pendingDir, "$baseName.png")
        imageFile.writeBytes(imageBytes)
        Log.i(TAG, "PNG image saved to pending: ${imageFile.absolutePath}")
        imageFile
    }

    suspend fun copyImageToPending(context: Context, sourceUri: Uri): File = withContext(Dispatchers.IO) {
        checkInitialized()
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("无法打开图片: $sourceUri")

        val imageBytes = inputStream.use { it.readBytes() }
        Log.i(TAG, "Read ${imageBytes.size} bytes from Uri: $sourceUri")

        val savedFile = saveImageToPending(imageBytes)
        Log.i(TAG, "Image copied from gallery to pending: ${savedFile.absolutePath}")
        return@withContext savedFile
    }

    fun getLatestPendingImage(): File? {
        checkInitialized()
        // Corrected: Look for .png files
        val pngFiles = pendingDir.listFiles { file ->
            file.isFile && file.extension.equals("png", ignoreCase = true)
        }
        if (pngFiles.isNullOrEmpty()) {
            return null
        }
        return pngFiles.maxByOrNull { it.lastModified() }
    }

    suspend fun archiveAnalyzedData(pendingImageFile: File, result: AnalysisResult) = withContext(Dispatchers.IO) {
        checkInitialized()
        val baseName = pendingImageFile.nameWithoutExtension

        // Corrected: Ensure the result object has the final .png imageName before saving.
        val consistentResult = result.copy(imageName = pendingImageFile.name)

        Log.i(TAG, "Archiving analyzed data for: $baseName")

        saveAnalysisResult(baseName, consistentResult)
        movePendingImageToHistory(pendingImageFile)
        Log.i(TAG, "Successfully archived: $baseName")
    }

    private suspend fun saveAnalysisResult(baseName: String, result: AnalysisResult) = withContext(Dispatchers.IO) {
        val jsonFile = File(historyDir, "$baseName.json")
        val jsonString = Json.encodeToString(result)
        jsonFile.writeText(jsonString)
        Log.i(TAG, "Analysis result saved: ${jsonFile.absolutePath}")
    }

    private suspend fun movePendingImageToHistory(pendingImageFile: File) = withContext(Dispatchers.IO) {
        checkInitialized()

        if (!pendingImageFile.exists()) {
            Log.w(TAG, "Attempted to move a non-existent file: ${pendingImageFile.absolutePath}")
            return@withContext
        }

        val destinationFile = File(historyDir, pendingImageFile.name)

        try {
            pendingImageFile.copyTo(destinationFile, overwrite = true)
            Log.i(TAG, "Successfully copied to history: ${destinationFile.absolutePath}")

            if (destinationFile.exists() && destinationFile.length() == pendingImageFile.length()) {
                if (!pendingImageFile.delete()) {
                    Log.e(TAG, "Failed to delete original pending file: ${pendingImageFile.absolutePath}")
                }
            } else {
                 throw IOException("Verification failed after copying to history. Original file kept.")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error moving file to history: ${e.message}")
            throw e
        }
    }

    private fun createDirectoryIfNeeded(directory: File, name: String) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Failed to create $name directory: ${directory.absolutePath}")
        }
    }

    private fun generateUniqueFileNameBase(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US)
        return "FLUORO_${dateFormat.format(Date())}"
    }

    private fun checkInitialized() {
        if (!this::rootDirectory.isInitialized) {
            throw IllegalStateException("FileManager has not been initialized. Ensure you call initialize() in your Application class.")
        }
    }
}
