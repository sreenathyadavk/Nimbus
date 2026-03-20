package com.localcloud.photosclient.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.localcloud.photosclient.data.AppDatabase
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.network.ApiClient
import com.localcloud.photosclient.utils.HashUtils
import android.content.ContentUris
import android.provider.MediaStore
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import com.localcloud.photosclient.network.ProgressRequestBody
import kotlinx.coroutines.launch
import com.localcloud.photosclient.data.preferences.SettingsDataStore
import java.io.File
import okio.source

class SyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = AppDatabase.getDatabase(appContext)
    private val mediaDao = db.mediaDao()
    private val apiService = ApiClient.getService(appContext)
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

                var inputStream: java.io.InputStream? = null
                try {
                    inputStream = appContext.contentResolver.openInputStream(uri)
                } catch (e: Exception) {
                    // fallthrough, inputStream remains null
                }
                
                if (inputStream == null) {
                    // File deleted locally before uploading or permission denied
                    Log.d("SYNC_DEBUG", "SyncWorker: InputStream is null for ID: ${media.id}, file may be deleted or permission denied")
                    mediaDao.updateStatus(media.id, "FAILED", media.retryCount, System.currentTimeMillis())
                    continue
                }
                inputStream.close()

                var hash = media.hash
                if (hash.isEmpty()) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        hash = HashUtils.calculateSHA256(stream)
                        Log.d("SYNC_DEBUG", "SyncWorker: Calculated SHA256 length: ${hash.length}")
                    }
                    mediaDao.updateMedia(media.copy(hash = hash))
                } else {
                    Log.d("SYNC_DEBUG", "SyncWorker: Using existing SHA256")
                }

                // Step 1: Check Hash
                val hashCheckResponse = apiService.checkHash(hash)
                if (hashCheckResponse.isSuccessful && hashCheckResponse.body()?.exists == true) {
                    // Duplicate exists on server, mark as success
                    mediaDao.updateStatus(media.id, "SUCCESS", media.retryCount, System.currentTimeMillis())
                    continue
                }

                // Step 2: Upload
                val streamToUpload = appContext.contentResolver.openInputStream(uri)
                if (streamToUpload == null) {
                    mediaDao.updateStatus(media.id, "FAILED", media.retryCount, System.currentTimeMillis())
                    continue
                }
                
                var fileSize = 0L
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }

                val requestBody = ProgressRequestBody(
                    contentType = media.mediaType.toMediaTypeOrNull(),
                    inputStream = streamToUpload,
                    contentLength = fileSize,
                    scope = this@coroutineScope,
                    onProgressUpdate = { progress ->
                        mediaDao.updateProgress(media.id, progress)
                    }
                )
                
                val filename = File(media.path).name
                val body = MultipartBody.Part.createFormData("file", filename, requestBody)
                
                // Using exact imports to fix OKHttp toRequestBody missing string dependency error if they arise:
                val hashBody = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), hash)
                val dateBody = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), formatIsoDate(media.dateAdded))

                Log.d("SYNC_DEBUG", "SyncWorker: Executing Upload API call for ID: ${media.id}")
                val uploadResponse = apiService.uploadMedia(body, hashBody, dateBody)
                
                Log.d("SYNC_DEBUG", "SyncWorker: Upload API response code: ${uploadResponse.code()}, message: ${uploadResponse.message()}")
                
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
            // Revert back to PENDING so it gets picked up, and increment retry
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
