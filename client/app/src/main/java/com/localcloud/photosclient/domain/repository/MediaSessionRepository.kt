package com.localcloud.photosclient.domain.repository

import com.localcloud.photosclient.domain.model.MediaItem
import kotlinx.coroutines.flow.StateFlow

interface MediaSessionRepository {
    val activeMediaList: StateFlow<List<MediaItem>>
    fun setActiveMediaList(media: List<MediaItem>)
}
