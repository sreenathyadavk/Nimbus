package com.localcloud.photosclient.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

enum class SyncState {
    NOT_SYNCED,
    SYNCED
}

enum class UploadStatus {
    PENDING,
    UPLOADING,
    SUCCESS,
    FAILED
}

enum class LocalAvailability {
    LOCAL_AVAILABLE,
    CLOUD_ONLY
}

@Entity(
    tableName = "local_media",
    indices = [
        androidx.room.Index(value = ["uploadStatus"]),
        androidx.room.Index(value = ["dateAdded"]),
        androidx.room.Index(value = ["path"])
    ]
)
data class LocalMedia(
    @PrimaryKey val id: Long, // MediaStore ID as primary key since hash collision is impossible locally for same item
    val path: String,
    val hash: String,
    val uploadStatus: String, // PENDING, UPLOADING, SUCCESS, FAILED
    val progress: Int = 0,
    val retryCount: Int = 0,
    val lastAttempt: Long = 0L,
    val dateAdded: Long = 0L,
    val mediaType: String,
    val duration: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val bucketName: String = "",
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val remoteId: String? = null, // Backend ID for deletion/sync
    val syncState: SyncState = SyncState.NOT_SYNCED,
    val localAvailability: LocalAvailability = LocalAvailability.LOCAL_AVAILABLE
) {
    val localUri: String get() {
        val baseUri = if (mediaType.startsWith("video", ignoreCase = true)) {
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return android.content.ContentUris.withAppendedId(baseUri, id).toString()
    }
}
