package com.localcloud.photosclient.data.repository

import com.localcloud.photosclient.domain.model.MediaItem
import com.localcloud.photosclient.domain.repository.MediaSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaSessionRepositoryImpl @Inject constructor() : MediaSessionRepository {
    private val _activeMediaList = MutableStateFlow<List<MediaItem>>(emptyList())
    override val activeMediaList: StateFlow<List<MediaItem>> = _activeMediaList.asStateFlow()

    override fun setActiveMediaList(media: List<MediaItem>) {
        _activeMediaList.value = media
    }
}
