package com.localcloud.photosclient.presentation.settings

import android.app.Application
import android.content.ContentResolver
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localcloud.photosclient.data.AppDatabase
import com.localcloud.photosclient.data.LocalAvailability
import com.localcloud.photosclient.domain.model.MediaItem
import com.localcloud.photosclient.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class FreeUpSpaceState(
    val removableMedia: List<com.localcloud.photosclient.data.LocalMedia> = emptyList(),
    val totalSize: Long = 0L,
    val formattedSize: String = "0 B",
    val isLoading: Boolean = true
)

@HiltViewModel
class FreeUpSpaceViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val mediaDao = AppDatabase.getDatabase(application).mediaDao()
    private val contentResolver: ContentResolver = application.contentResolver

    private val _state = MutableStateFlow(FreeUpSpaceState())
    val state: StateFlow<FreeUpSpaceState> = _state.asStateFlow()

    init {
        loadRemovableMedia()
    }

    private fun loadRemovableMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true)
            
            val localMediaItems = mediaDao.getRemovableMedia()
            var totalSize = 0L
            
            localMediaItems.forEach { local ->
                totalSize += File(local.path).length()
            }

            _state.value = FreeUpSpaceState(
                removableMedia = localMediaItems,
                totalSize = totalSize,
                formattedSize = formatCacheSize(totalSize),
                isLoading = false
            )
        }
    }

    fun removeLocalCopies() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            withContext(Dispatchers.IO) {
                val itemsToRemove = _state.value.removableMedia
                
                for (item in itemsToRemove) {
                    try {
                        val file = File(item.path)
                        
                        // Delete via ContentResolver first to ensure MediaStore is updated
                        // Wait, we need the MediaStore ID. We saved it as `item.id`.
                        val uri = android.content.ContentUris.withAppendedId(
                            if (item.mediaType.startsWith("video")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            item.id
                        )

                        val deletedRows = contentResolver.delete(uri, null, null)
                        
                        // Fallback: Physical delete if MediaStore delete failed or returned 0
                        var physicallyDeleted = false
                        if (file.exists()) {
                            physicallyDeleted = file.delete()
                        }
                        
                        if (deletedRows > 0 || physicallyDeleted) {
                            Log.d("FreeUpSpace", "Successfully removed local copy for ID ${item.id}")
                            mediaDao.updateLocalAvailability(item.id, LocalAvailability.CLOUD_ONLY)
                        } else {
                            Log.e("FreeUpSpace", "Failed to remove local copy for ID ${item.id} - Not found or permission denied")
                        }
                    } catch (e: Exception) {
                        Log.e("FreeUpSpace", "Exception removing local copy for ID ${item.id}", e)
                        // DO NOT mark as CLOUD_ONLY if exception occurs
                    }
                }
            }
            // Reload the list to refresh UI
            loadRemovableMedia()
        }
    }

    private fun formatCacheSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}
