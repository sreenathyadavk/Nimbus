package com.localcloud.photosclient.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class AlbumStats(
    val bucketName: String,
    val lastDate: Long,
    val count: Int,
    val lastMediaId: Long
)

@Dao
interface MediaDao {

    @Query("SELECT * FROM local_media WHERE is_deleted = 0 ORDER BY dateAdded DESC")
    fun getAllMediaFlow(): Flow<List<LocalMedia>>

    @Query("SELECT * FROM local_media WHERE is_favorite = 1 AND is_deleted = 0 ORDER BY dateAdded DESC")
    fun getFavorites(): Flow<List<LocalMedia>>

    @Query("SELECT * FROM local_media WHERE is_deleted = 1 ORDER BY dateAdded DESC")
    fun getDeleted(): Flow<List<LocalMedia>>

    @Query("SELECT * FROM local_media WHERE bucketName = :bucketName AND is_deleted = 0 ORDER BY dateAdded DESC")
    fun getMediaByBucketFlow(bucketName: String): Flow<List<LocalMedia>>

    @Query("SELECT bucketName, MAX(dateAdded) as lastDate, COUNT(*) as count, (SELECT id FROM local_media lm2 WHERE lm2.bucketName = local_media.bucketName AND lm2.is_deleted = 0 ORDER BY dateAdded DESC LIMIT 1) as lastMediaId FROM local_media WHERE is_deleted = 0 GROUP BY bucketName ORDER BY lastDate DESC")
    fun getAlbumsFlow(): Flow<List<AlbumStats>>

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

    @Query("UPDATE local_media SET uploadStatus = :status WHERE id = :id")
    suspend fun updateUploadStatus(id: Long, status: String)

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

    @Query("SELECT * FROM local_media WHERE uploadStatus = 'SUCCESS' AND localAvailability = :availability AND is_deleted = 0")
    suspend fun getRemovableMedia(
        availability: LocalAvailability = LocalAvailability.LOCAL_AVAILABLE
    ): List<LocalMedia>

    @Query("UPDATE local_media SET is_favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE local_media SET is_deleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean)

    @Query("DELETE FROM local_media WHERE id = :id")
    suspend fun permanentlyDelete(id: Long)

    @Query("UPDATE local_media SET localAvailability = :availability WHERE id = :id")
    suspend fun updateLocalAvailability(id: Long, availability: LocalAvailability)

    @Query("SELECT * FROM local_media WHERE is_deleted = 1 AND deletedAt < :timestamp")
    suspend fun getItemsDeletedBefore(timestamp: Long): List<LocalMedia>
}
