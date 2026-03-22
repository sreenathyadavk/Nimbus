package com.localcloud.photosclient.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log
import com.localcloud.photosclient.data.AppDatabase
import dagger.hilt.android.EntryPointAccessors
import com.localcloud.photosclient.di.DatabaseModule

class AutoDeleteWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val mediaDao = EntryPointAccessors.fromApplication(
        appContext,
        DatabaseModule.MediaDaoEntryPoint::class.java
    ).mediaDao()

    override suspend fun doWork(): Result {
        return try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val expiredItems = mediaDao.getItemsDeletedBefore(thirtyDaysAgo)

            expiredItems.forEach { item ->
                try {
                    val baseUri = if (item.mediaType.startsWith("video", ignoreCase = true)) {
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val uri = android.content.ContentUris.withAppendedId(baseUri, item.id)
                    applicationContext.contentResolver.delete(uri, null, null)
                    mediaDao.permanentlyDelete(item.id)
                } catch (e: Exception) {
                    Log.e("AutoDeleteWorker", "Failed to delete item ${item.id}", e)
                    // Still remove from DB if it fails (might be already gone)
                    mediaDao.permanentlyDelete(item.id)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("AutoDeleteWorker", "Error in auto-delete", e)
            Result.retry()
        }
    }
}
