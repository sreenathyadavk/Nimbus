package com.localcloud.photosclient.data.repository

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val cacheDir = File(context.cacheDir, "cloud_media")
    private val maxCacheSize = 500L * 1024 * 1024 // 500 MB

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    suspend fun getOrFetchMedia(remoteId: String, downloadUrl: String): File? = withContext(Dispatchers.IO) {
        val cachedFile = File(cacheDir, remoteId)
        
        // 1. Check if already in cache
        if (cachedFile.exists() && cachedFile.length() > 0) {
            cachedFile.setLastModified(System.currentTimeMillis()) // Update LRU
            return@withContext cachedFile
        }

        // 2. Fetch from cloud
        try {
            Log.d("CloudCache", "Cache miss for $remoteId, downloading from $downloadUrl")
            val request = Request.Builder().url(downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("CloudCache", "Failed to download $remoteId. Code: ${response.code}")
                return@withContext null
            }

            val body: ResponseBody? = response.body
            if (body != null) {
                // Before downloading, ensure we have space
                enforceCacheLimit(body.contentLength())

                val inputStream: InputStream = body.byteStream()
                val tempFile = File(cacheDir, "${remoteId}.tmp")
                val outputStream = FileOutputStream(tempFile)

                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                // Successful download, rename temp to final
                if (tempFile.renameTo(cachedFile)) {
                    Log.d("CloudCache", "Successfully cached $remoteId. Size: ${cachedFile.length()}")
                    return@withContext cachedFile
                } else {
                    Log.e("CloudCache", "Failed to rename temp file for $remoteId")
                    tempFile.delete()
                }
            } else {
                Log.e("CloudCache", "ResponseBody was null for $remoteId")
            }
        } catch (e: Exception) {
            Log.e("CloudCache", "Exception during download of $remoteId", e)
        }
        return@withContext null
    }

    private fun enforceCacheLimit(incomingSize: Long) {
        var currentSize = getCacheSize()
        if (currentSize + incomingSize <= maxCacheSize) return

        Log.d("CloudCache", "Cache limit reached ($currentSize bytes), evicting old files...")
        
        // List files, sort by oldest first (LRU)
        val files = cacheDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        val sortedFiles = files.sortedBy { it.lastModified() }

        for (file in sortedFiles) {
            val size = file.length()
            if (file.delete()) {
                currentSize -= size
                Log.d("CloudCache", "Evicted ${file.name} ($size bytes)")
            }
            if (currentSize + incomingSize <= maxCacheSize) {
                break
            }
        }
        Log.d("CloudCache", "Eviction complete. New size: $currentSize bytes")
    }

    private fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
