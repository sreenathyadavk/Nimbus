package com.localcloud.photosclient.presentation.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.data.repository.MediaRepository
import com.localcloud.photosclient.domain.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class Album(
    val name: String,
    val coverUri: String?,
    val count: Int,
    val isSpecial: Boolean = false,
    val folderPath: String? = null
)

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    val albums: StateFlow<List<Album>> = repository.allMedia
        .map { mediaList ->
            val folderMap = mediaList.groupBy { it.path.substringBeforeLast("/") }
            
            val deviceAlbums = folderMap.map { (path, items) ->
                Album(
                    name = path.substringAfterLast("/"),
                    coverUri = items.firstOrNull()?.uri,
                    count = items.size,
                    folderPath = path
                )
            }.sortedBy { it.name }

            val specialAlbums = listOf(
                Album("Recents", mediaList.firstOrNull()?.uri, mediaList.size, true),
                Album("Favorites", mediaList.filter { it.isFavorite }.firstOrNull()?.uri, mediaList.count { it.isFavorite }, true),
                Album("Videos", mediaList.filter { it.mediaType.startsWith("video") }.firstOrNull()?.uri, mediaList.count { it.mediaType.startsWith("video") }, true)
            ).filter { it.count > 0 }

            specialAlbums + deviceAlbums
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
