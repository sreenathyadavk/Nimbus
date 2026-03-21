package com.localcloud.photosclient.presentation.albums

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localcloud.photosclient.data.AppDatabase
import com.localcloud.photosclient.data.LocalMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class Album(
    val name: String,
    val coverUri: String?,
    val count: Int,
    val isSpecial: Boolean = false,
    val bucketName: String? = null
)

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val mediaDao = AppDatabase.getDatabase(application).mediaDao()

    val albums: StateFlow<List<Album>> = mediaDao.getAllMediaFlow()
        .map { mediaList ->
            if (mediaList.isNotEmpty()) {
                Log.d("ALBUM_DEBUG", "LocalMedia fields Sample: ${mediaList.first()}")
            }

            // Group by bucketName
            val bucketMap = mediaList.groupBy { 
                it.bucketName.takeIf { name -> name.isNotBlank() } ?: "Other"
            }
            
            Log.d("ALBUM_DEBUG", "Groups found: ${bucketMap.keys}")

            val deviceAlbums = bucketMap.map { (name, items) ->
                Album(
                    name = name,
                    coverUri = items.firstOrNull()?.path, // Use path for now, will fix URI in screen
                    count = items.size,
                    bucketName = name
                )
            }.sortedBy { it.name }

            val specialAlbums = listOf(
                Album("Recents", mediaList.firstOrNull()?.path, mediaList.size, true),
                Album("Favorites", mediaList.filter { it.isFavorite }.firstOrNull()?.path, mediaList.count { it.isFavorite }, true),
                Album("Videos", mediaList.filter { it.mediaType.startsWith("video") }.firstOrNull()?.path, mediaList.count { it.mediaType.startsWith("video") }, true)
            ).filter { it.count > 0 }

            specialAlbums + deviceAlbums
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
