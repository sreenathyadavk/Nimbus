package com.localcloud.photosclient.presentation.albums

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localcloud.photosclient.data.AppDatabase
import com.localcloud.photosclient.data.LocalMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class AlbumUiModel(
    val id: String,
    val name: String,
    val count: Int,
    val coverUri: String,
    val filter: (LocalMedia) -> Boolean
)

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val mediaDao = AppDatabase.getDatabase(application).mediaDao()

    val albumsFlow: StateFlow<List<AlbumUiModel>> = mediaDao.getAllMediaFlow()
        .map { allMedia ->
            val validMedia = allMedia.filter { !it.isDeleted }
            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

            val albums = mutableListOf<AlbumUiModel>()

            // 1. Recent (last 30 days)
            val recentMedia = validMedia.filter { it.dateAdded > thirtyDaysAgo }
            if (recentMedia.isNotEmpty()) {
                albums.add(
                    AlbumUiModel(
                        id = "recent",
                        name = "Recent",
                        count = recentMedia.size,
                        coverUri = getMediaUri(recentMedia.first()),
                        filter = { it.dateAdded > thirtyDaysAgo && !it.isDeleted }
                    )
                )
            }

            // 2. Favourites
            val favoriteMedia = validMedia.filter { it.isFavorite }
            if (favoriteMedia.isNotEmpty()) {
                albums.add(
                    AlbumUiModel(
                        id = "favorites",
                        name = "Favourites",
                        count = favoriteMedia.size,
                        coverUri = getMediaUri(favoriteMedia.first()),
                        filter = { it.isFavorite && !it.isDeleted }
                    )
                )
            }

            // 3. Device Folders
            val folderGroups = validMedia.groupBy { it.bucketName }
                .filter { it.key.isNotEmpty() }
                .toSortedMap()

            folderGroups.forEach { (bucketName, items) ->
                albums.add(
                    AlbumUiModel(
                        id = "folder_$bucketName",
                        name = bucketName,
                        count = items.size,
                        coverUri = getMediaUri(items.sortedByDescending { it.dateAdded }.first()),
                        filter = { it.bucketName == bucketName && !it.isDeleted }
                    )
                )
            }

            albums
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun getMediaByAlbum(albumId: String): Flow<List<LocalMedia>> {
        return mediaDao.getAllMediaFlow().map { allMedia ->
            val validMedia = allMedia.filter { !it.isDeleted }
            when {
                albumId == "recent" -> {
                    val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                    validMedia.filter { it.dateAdded > thirtyDaysAgo }.sortedByDescending { it.dateAdded }
                }
                albumId == "favorites" -> {
                    validMedia.filter { it.isFavorite }.sortedByDescending { it.dateAdded }
                }
                albumId.startsWith("folder_") -> {
                    val bucketName = albumId.removePrefix("folder_")
                    validMedia.filter { it.bucketName == bucketName }.sortedByDescending { it.dateAdded }
                }
                else -> emptyList()
            }
        }
    }

    private fun getMediaUri(media: LocalMedia): String {
        val baseUri = if (media.mediaType.startsWith("video", ignoreCase = true)) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(baseUri, media.id).toString()
    }
}
