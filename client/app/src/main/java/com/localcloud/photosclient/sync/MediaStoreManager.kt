package com.localcloud.photosclient.sync

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.localcloud.photosclient.data.AppDatabase
import com.localcloud.photosclient.data.LocalMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File

class MediaStoreManager(private val context: Context, private val settingsDataStore: com.localcloud.photosclient.data.preferences.SettingsDataStore) {

    private val db = AppDatabase.getDatabase(context)
    private val mediaDao = db.mediaDao()

    suspend fun scanAndSyncMediaStore() = withContext(Dispatchers.IO) {
        Log.d("SCAN_DEBUG", "Starting MediaStore scan")
        val autoSync = settingsDataStore.autoSyncEnabled.first()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val selection = (MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                + " OR "
                + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        val queryUri = MediaStore.Files.getContentUri("external")

        context.contentResolver.query(
            queryUri,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val count = cursor.count
            Log.d("SCAN_DEBUG", "Found $count items in MediaStore")

            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val durationCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
            val widthCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
            val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(pathCol)
                val dateAdded = cursor.getLong(dateCol) * 1000L // convert to ms
                val mediaTypeInt = cursor.getInt(typeCol)
                val mediaTypeStr = if (mediaTypeInt == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) "video/mp4" else "image/jpeg"
                val duration = if (durationCol != -1) cursor.getLong(durationCol) else null
                val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                val bucketName = if (bucketCol != -1) cursor.getString(bucketCol) ?: "Unknown" else "Unknown"

                val existsInRoom = mediaDao.exists(id)
                if (!existsInRoom) {
                    val localMedia = LocalMedia(
                        id = id,
                        path = path,
                        hash = "",
                        uploadStatus = if (autoSync) "PENDING" else "NONE",
                        dateAdded = dateAdded,
                        mediaType = mediaTypeStr,
                        duration = duration,
                        width = width,
                        height = height,
                        bucketName = bucketName
                    )
                    Log.d("SCAN_DEBUG", "Inserting into Room: ID=$id, Status=${localMedia.uploadStatus}")
                    mediaDao.insertMedia(localMedia)
                }
            }
        }
        
        Log.d("SCAN_DEBUG", "MediaStore scan completed. Triggering SyncManager.")
        SyncManager.forceSync(context)
    }
}
