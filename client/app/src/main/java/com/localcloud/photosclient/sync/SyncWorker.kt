package com.localcloud.photosclient.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.localcloud.photosclient.data.AppDatabase
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.utils.HashUtils
import android.content.ContentUris
import android.provider.MediaStore
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import com.localcloud.photosclient.network.ProgressRequestBody
import kotlinx.coroutines.launch
import com.localcloud.photosclient.data.preferences.SettingsDataStore
import com.localcloud.photosclient.di.NetworkModule
import dagger.hilt.android.EntryPointAccessors
import java.io.File

class SyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // Fetch Hilt-injected dependencies via EntryPoint
    private val apiService = EntryPointAccessors.fromApplication(
        appContext,
        NetworkModule.ApiServiceEntryPoint::class.java
    ).apiService()

    private val db = AppDatabase.getDatabase(appContext)
    private val mediaDao = db.mediaDao()
    private val settingsDataStore = SettingsDataStore(appContext)

    override suspend fun doWork(): Result = kotlinx.coroutines.coroutineScope {
        val pendingUploads = mediaDao.getPendingUploads()

        Log.d("SYNC_DEBUG", "SyncWorker: Started doWork, pendingUploads count: ${pendingUploads.size}")

        if (pendingUploads.isEmpty()) {
            return@coroutineScope Result.success()
        }

        var allSuccess = true
        var shouldRetry = false

        for (media in pendingUploads) {
            try {
                Log.d("SYNC_DEBUG", "SyncWorker: Processing item ID: ${media.id}, Path: ${media.path}")
                mediaDao.updateStatus(media.id, "UPLOADING", media.retryCount, System.currentTimeMillis())

                val uri = if (media.mediaType.startsWith("video")) {
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, media.id)
                } else {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, media.id)
                }

                // Step 1: Compute hash — open stream, use it, CLOSE it.
                var hash = media.hash
                if (hash.isEmpty()) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        hash = HashUtils.calculateSHA256(stream)
                        Log.d("SYNC_DEBUG", "SyncWorker: Computed SHA256 length: ${hash.length}")
                    }
                    if (hash.isEmpty()) {
                        Log.e("SYNC_DEBUG", "SyncWorker: Failed to compute hash for ID: ${media.id}")
                        mediaDao.updateStatus(media.id, "FAILED", media.retryCount, System.currentTimeMillis())
                        continue
                    }
                    mediaDao.updateMedia(media.copy(hash = hash))
                } else {
                    Log.d("SYNC_DEBUG", "SyncWorker: Using existing SHA256")
                }

                // Step 2: Check if server already has this file (hash check uses a fresh call)
                val hashCheckResponse = apiService.checkHash(hash)
                if (hashCheckResponse.isSuccessful && hashCheckResponse.body()?.exists == true) {
                    Log.d("SYNC_DEBUG", "SyncWorker: Duplicate detected for ID: ${media.id}, marking SUCCESS")
                    mediaDao.updateStatus(media.id, "SUCCESS", media.retryCount, System.currentTimeMillis())
                    continue
                }

                // Step 3: Read file size from MediaStore
                var fileSize = 0L
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }

                // Step 4: Verify file is readable with a fresh stream before committing to upload
                val readable = appContext.contentResolver.openInputStream(uri)?.use { true } ?: false
                if (!readable) {
                    Log.e("SYNC_DEBUG", "SyncWorker: File not readable for ID: ${media.id}")
                    mediaDao.updateStatus(media.id, "FAILED", media.retryCount, System.currentTimeMillis())
                    continue
                }

                // Step 5: Upload — ProgressRequestBody receives a factory lambda.
                // Each writeTo() call (including OkHttp retries) opens a FRESH stream.
                val requestBody = ProgressRequestBody(
                    contentType = media.mediaType.toMediaTypeOrNull(),
                    streamFactory = {
                        appContext.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Cannot open stream for uri: $uri")
                    },
                    contentLength = fileSize,
                    scope = this@coroutineScope,
                    onProgressUpdate = { progress ->
                        mediaDao.updateProgress(media.id, progress)
                    }
                )

                val filename = File(media.path).name
                val body = MultipartBody.Part.createFormData("file", filename, requestBody)
                val hashBody = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), hash)
                val dateBody = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), formatIsoDate(media.dateAdded))

                Log.d("SYNC_DEBUG", "SyncWorker: Uploading ID: ${media.id}, size: $fileSize bytes")
                val uploadResponse = apiService.uploadMedia(body, hashBody, dateBody)

                Log.d("SYNC_DEBUG", "SyncWorker: Upload response: ${uploadResponse.code()} ${uploadResponse.message()}")

                if (uploadResponse.isSuccessful) {
                    val remoteMedia = uploadResponse.body()
                    mediaDao.updateStatus(
                        id = media.id,
                        status = "SUCCESS",
                        retryCount = media.retryCount,
                        timestamp = System.currentTimeMillis(),
                        remoteId = remoteMedia?.id
                    )
                } else {
                    Log.e("SyncWorker", "Upload failed: ${uploadResponse.errorBody()?.string()}")
                    handleFailure(media)
                    allSuccess = false
                    shouldRetry = true
                }

            } catch (e: Exception) {
                Log.e("SyncWorker", "Exception during sync", e)
                handleFailure(media)
                allSuccess = false
                shouldRetry = true
            }
        }

        if (allSuccess) {
            settingsDataStore.updateLastSyncTime(System.currentTimeMillis())
            Result.success()
        } else if (shouldRetry) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    private suspend fun handleFailure(media: LocalMedia) {
        if (media.retryCount < 5) {
            mediaDao.updateStatus(
                id = media.id,
                status = "PENDING",
                retryCount = media.retryCount + 1,
                timestamp = System.currentTimeMillis(),
                remoteId = media.remoteId
            )
        } else {
            mediaDao.updateStatus(
                id = media.id,
                status = "FAILED",
                retryCount = media.retryCount,
                timestamp = System.currentTimeMillis(),
                remoteId = media.remoteId
            )
        }
    }

    private fun formatIsoDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
