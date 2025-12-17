package com.example.fluorosignalapp.backend

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

/**
 * 后端存储服务 - 管理图像和分析数据的存储
 * 实现IImageStorageBackendService接口
 */
class ImageStorageBackendService(private val context: Context) : IImageStorageBackendService {

    private val TAG = "StorageBackend"
    private val storageDir: File = File(context.cacheDir, "backend_storage").apply {
        if (!exists()) mkdirs()
    }
    private val imageDir: File = File(storageDir, "images").apply {
        if (!exists()) mkdirs()
    }
    private val archiveDir: File = File(storageDir, "archive").apply {
        if (!exists()) mkdirs()
    }

    private val imageRegistry = mutableMapOf<String, ImageMetadata>()

    /**
     * 保存图像到后端存储
     */
    override suspend fun saveImage(imageName: String, imageFile: File): String {
        return withContext(Dispatchers.IO) {
            try {
                val imageId = generateImageId()
                val destFile = File(imageDir, "$imageId.jpg")

                // 复制图像文件
                imageFile.copyTo(destFile, overwrite = true)

                // 记录元数据
                val metadata = ImageMetadata(
                    imageId = imageId,
                    originalName = imageName,
                    filePath = destFile.absolutePath,
                    fileSize = destFile.length(),
                    timestamp = System.currentTimeMillis()
                )
                imageRegistry[imageId] = metadata

                Log.i(TAG, "Saved image: $imageId from $imageName (${destFile.length()} bytes)")

                imageId

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save image: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * 获取已保存的图像
     */
    override suspend fun getImage(imageId: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val metadata = imageRegistry[imageId] ?: run {
                    Log.w(TAG, "Image not found in registry: $imageId")
                    return@withContext null
                }

                val file = File(metadata.filePath)
                if (file.exists()) {
                    Log.d(TAG, "Retrieved image: $imageId")
                    file
                } else {
                    Log.w(TAG, "Image file not found: ${metadata.filePath}")
                    null
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to get image: ${e.message}", e)
                null
            }
        }
    }

    /**
     * 删除保存的图像
     */
    override suspend fun deleteImage(imageId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val metadata = imageRegistry[imageId] ?: return@withContext false

                val file = File(metadata.filePath)
                val deleted = if (file.exists()) file.delete() else true

                if (deleted) {
                    imageRegistry.remove(imageId)
                    Log.i(TAG, "Deleted image: $imageId")
                }

                deleted

            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete image: ${e.message}", e)
                false
            }
        }
    }

    /**
     * 获取存储统计信息
     */
    override fun getStorageStats(): Long {
        return try {
            var totalSize = 0L
            imageRegistry.forEach { (_, metadata) ->
                val file = File(metadata.filePath)
                if (file.exists()) totalSize += file.length()
            }
            Log.d(TAG, "Storage stats: $totalSize bytes used")
            totalSize
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get storage stats: ${e.message}", e)
            0L
        }
    }

    /**
     * 存档历史分析
     */
    suspend fun archiveAnalysis(analysisId: String, imageId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sourceMetadata = imageRegistry[imageId] ?: return@withContext false
                val sourceFile = File(sourceMetadata.filePath)

                if (!sourceFile.exists()) return@withContext false

                val archiveFile = File(archiveDir, "$analysisId.jpg")
                sourceFile.copyTo(archiveFile, overwrite = true)

                Log.i(TAG, "Archived analysis: $analysisId with image $imageId")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Failed to archive analysis: ${e.message}", e)
                false
            }
        }
    }

    /**
     * 清理过期文件
     */
    suspend fun cleanup(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L): Int {
        return withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val toDelete = imageRegistry.filter { (_, metadata) ->
                    (now - metadata.timestamp) > maxAgeMs
                }

                var cleanedCount = 0
                toDelete.forEach { (imageId, _) ->
                    if (deleteImage(imageId)) cleanedCount++
                }

                Log.i(TAG, "Cleanup complete: removed $cleanedCount old images")
                cleanedCount

            } catch (e: Exception) {
                Log.e(TAG, "Cleanup failed: ${e.message}", e)
                0
            }
        }
    }

    private fun generateImageId(): String {
        return "IMG_${UUID.randomUUID().toString().take(12).uppercase()}"
    }

    /**
     * 图像元数据
     */
    data class ImageMetadata(
        val imageId: String,
        val originalName: String,
        val filePath: String,
        val fileSize: Long,
        val timestamp: Long
    )

    companion object {
        private var instance: ImageStorageBackendService? = null

        fun getInstance(context: Context): ImageStorageBackendService {
            return instance ?: ImageStorageBackendService(context).also { instance = it }
        }
    }
}
