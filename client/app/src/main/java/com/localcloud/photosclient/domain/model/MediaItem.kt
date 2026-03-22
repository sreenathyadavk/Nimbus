package com.localcloud.photosclient.domain.model

import android.net.Uri

import com.localcloud.photosclient.data.LocalAvailability

enum class MediaType {
    IMAGE, VIDEO
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val type: MediaType,
    val displayName: String,
    val size: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val duration: Long? = null,
    val remoteId: String? = null,
    val uploadStatus: String = "SUCCESS", // Default to success if coming from cloud, but overridden in mapping
    val localAvailability: LocalAvailability = LocalAvailability.LOCAL_AVAILABLE,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false
)
