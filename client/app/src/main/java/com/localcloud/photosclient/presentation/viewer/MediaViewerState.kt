package com.localcloud.photosclient.presentation.viewer

import com.localcloud.photosclient.domain.model.MediaItem

data class MediaViewerState(
    val mediaItems: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val isUiVisible: Boolean = true,
    val isInfoSheetVisible: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val downloadedMediaMap: Map<String, String> = emptyMap()
)
