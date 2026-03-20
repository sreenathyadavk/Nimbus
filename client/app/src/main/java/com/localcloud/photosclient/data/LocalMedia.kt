package com.localcloud.photosclient.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncState {
    NOT_SYNCED,
    SYNCED
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
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val remoteId: String? = null, // Backend ID for deletion/sync
    val syncState: SyncState = SyncState.NOT_SYNCED,
    val localAvailability: LocalAvailability = LocalAvailability.LOCAL_AVAILABLE
)
