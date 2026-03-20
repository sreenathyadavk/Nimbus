package com.localcloud.photosclient.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM local_media ORDER BY dateAdded DESC")
    fun getAllMediaFlow(): Flow<List<LocalMedia>>

    @Query("SELECT * FROM local_media WHERE (uploadStatus = 'PENDING' OR uploadStatus = 'FAILED') AND retryCount < 5")
    suspend fun getPendingUploads(): List<LocalMedia>
    
    @Query("SELECT * FROM local_media WHERE id = :id")
    suspend fun getMediaById(id: Long): LocalMedia?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: LocalMedia)

    @Update
    suspend fun updateMedia(media: LocalMedia)

    @Query("UPDATE local_media SET uploadStatus = :status, retryCount = :retryCount, lastAttempt = :timestamp, progress = 0, remoteId = :remoteId WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, retryCount: Int, timestamp: Long, remoteId: String? = null)

    @Query("UPDATE local_media SET remoteId = :remoteId WHERE id = :id")
    suspend fun updateRemoteId(id: Long, remoteId: String)

    @Query("UPDATE local_media SET uploadStatus = :status, retryCount = 0, lastAttempt = :timestamp, progress = 0 WHERE id IN (:ids)")
    suspend fun setStatusForIds(ids: List<Long>, status: String, timestamp: Long)

    @Query("UPDATE local_media SET progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int)

    @Query("UPDATE local_media SET uploadStatus = 'PENDING', retryCount = 0, lastAttempt = :timestamp, progress = 0 WHERE uploadStatus NOT IN ('SUCCESS', 'PENDING', 'UPLOADING')")
    suspend fun queueAllUnsynced(timestamp: Long): Int

    @Query("DELETE FROM local_media WHERE id = :id")
    suspend fun deleteMediaById(id: Long)

    @Query("SELECT EXISTS(SELECT * FROM local_media WHERE id = :id)")
    suspend fun exists(id: Long): Boolean

    @Query("SELECT * FROM local_media WHERE syncState = :syncState AND localAvailability = :availability")
    suspend fun getRemovableMedia(
        syncState: SyncState = SyncState.SYNCED,
        availability: LocalAvailability = LocalAvailability.LOCAL_AVAILABLE
    ): List<LocalMedia>

    @Query("UPDATE local_media SET localAvailability = :availability WHERE id = :id")
    suspend fun updateLocalAvailability(id: Long, availability: LocalAvailability)
}
