package com.localcloud.photosclient.presentation.viewer

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localcloud.photosclient.domain.repository.MediaSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.localcloud.photosclient.data.repository.CloudCacheManager
import com.localcloud.photosclient.domain.model.MediaItem
import com.localcloud.photosclient.data.MediaDao
import com.localcloud.photosclient.sync.SyncManager
import android.app.Application
import javax.inject.Inject

// Models imported from separate files

sealed class MediaViewerUiEvent {
    object NavigateBack : MediaViewerUiEvent()
    data class ShowToast(val message: String) : MediaViewerUiEvent()
    data class ShareMedia(val media: MediaItem) : MediaViewerUiEvent()
}

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    private val application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val mediaSessionRepository: MediaSessionRepository,
    private val mediaDao: MediaDao,
    private val apiService: com.localcloud.photosclient.network.ApiService,
    private val cloudCacheManager: CloudCacheManager
) : ViewModel() {

    private val _state = MutableStateFlow(MediaViewerState())
    val state: StateFlow<MediaViewerState> = _state.asStateFlow()

    private val _uiEvent = MutableSharedFlow<MediaViewerUiEvent>()
    val uiEvent: SharedFlow<MediaViewerUiEvent> = _uiEvent.asSharedFlow()

    init {
        Log.d("MediaViewerViewModel", "ViewModel initialized")
        loadMediaList()
    }

    private fun loadMediaList() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            try {
                mediaSessionRepository.activeMediaList.collect { mediaList ->
                    if (mediaList.isEmpty()) {
                        Log.w("MediaViewerViewModel", "Guard: Media list is empty or deleted. Halting UI.")
                        _state.update { 
                            it.copy(
                                isLoading = false, 
                                error = "Gallery is empty or files were deleted.",
                                mediaItems = emptyList()
                            ) 
                        }
                        return@collect
                    }

                    val restoredIndex = savedStateHandle.get<Int>(KEY_CURRENT_INDEX)
                    val navStartIndex = savedStateHandle.get<Int>(KEY_START_INDEX) ?: 0
                    
                    val startIndex = restoredIndex ?: navStartIndex
                    
                    // Invalid startIndex guard + logging
                    if (startIndex !in mediaList.indices) {
                        Log.e("MediaViewerViewModel", "Guard: Invalid startIndex $startIndex out of bounds 0..${mediaList.size - 1}. Clamping.")
                    }
                    val safeIndex = startIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0))

                    if (restoredIndex == null) {
                        savedStateHandle[KEY_CURRENT_INDEX] = safeIndex
                    }

                    // 4K Memory Safety Log (Check if image size exceeds extremely large thresholds visually)
                    mediaList.getOrNull(safeIndex)?.let { item ->
                        val sizeMb = item.size / (1024f * 1024f)
                        if (sizeMb > 50f || item.width > 4000 || item.height > 4000) {
                            Log.w("MediaViewerViewModel", "Memory Safety Guard: Large 4K+ file detected: ${item.displayName} ($sizeMb MB, ${item.width}x${item.height}). Deferring scaling to Coil/Exo hardware decoder.")
                        }
                    }

                    Log.d("MediaViewerViewModel", "Loaded ${mediaList.size} items. Bound index: $safeIndex")

                    _state.update {
                        it.copy(
                            mediaItems = mediaList,
                            currentIndex = safeIndex,
                            isLoading = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("MediaViewerViewModel", "Error loading media list", e)
                _state.update { it.copy(isLoading = false, error = "Failed to load media.") }
            }
        }
    }

    fun onEvent(event: MediaViewerEvent) {
        when (event) {
            is MediaViewerEvent.OnPageChanged -> {
                Log.d("MediaViewerViewModel", "Page changed to: ${event.index}")
                _state.update { it.copy(currentIndex = event.index) }
                savedStateHandle[KEY_CURRENT_INDEX] = event.index
                
                // Pre-fetch current and adjacent items if they are cloud only
                val items = _state.value.mediaItems
                val indicesToFetch = listOf(event.index - 1, event.index, event.index + 1)
                
                indicesToFetch.forEach { idx ->
                    if (idx in items.indices) {
                        val item = items[idx]
                        if (item.localAvailability == com.localcloud.photosclient.data.LocalAvailability.CLOUD_ONLY) {
                            fetchCloudMedia(item)
                        }
                    }
                }
            }
            is MediaViewerEvent.ToggleUiVisibility -> {
                val newVisibility = !_state.value.isUiVisible
                Log.d("MediaViewerViewModel", "Toggling UI visibility to: $newVisibility")
                _state.update { it.copy(isUiVisible = newVisibility) }
            }
            MediaViewerEvent.ToggleInfoSheetVisibility -> {
                val newValue = !_state.value.isInfoSheetVisible
                _state.update { it.copy(isInfoSheetVisible = newValue) }
            }
            MediaViewerEvent.OnBackClick -> {
                Log.d("MediaViewerViewModel", "Back clicked")
            }
            MediaViewerEvent.OnRetry -> {
                Log.d("MediaViewerViewModel", "Retry clicked")
                loadMediaList()
            }
            MediaViewerEvent.OnShare -> {
                val currentItem = state.value.mediaItems.getOrNull(state.value.currentIndex)
                currentItem?.let {
                    viewModelScope.launch {
                        _uiEvent.emit(MediaViewerUiEvent.ShareMedia(it))
                    }
                }
            }
            MediaViewerEvent.OnDelete -> {
                val currentItem = state.value.mediaItems.getOrNull(state.value.currentIndex)
                currentItem?.let { item ->
                    viewModelScope.launch {
                        // 1. Delete from Backend (if remoteId exists)
                        item.remoteId?.let { rid ->
                            try {
                                val response = apiService.deleteMedia(rid)
                                if (response.isSuccessful) {
                                    Log.d("MediaViewerViewModel", "Successfully deleted from backend: $rid")
                                } else {
                                    Log.e("MediaViewerViewModel", "Failed to delete from backend: ${response.code()}")
                                }
                            } catch (e: Exception) {
                                Log.e("MediaViewerViewModel", "Error deleting from backend", e)
                            }
                        }

                        // 2. Delete from Filesystem
                        try {
                            val file = java.io.File(item.uri.path ?: "")
                            if (file.exists()) {
                                if (file.delete()) {
                                    Log.d("MediaViewerViewModel", "Successfully deleted local file: ${file.absolutePath}")
                                } else {
                                    Log.w("MediaViewerViewModel", "Failed to delete local file: ${file.absolutePath}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MediaViewerViewModel", "Error deleting local file", e)
                        }

                        // 3. Delete from Local Database
                        mediaDao.deleteMediaById(item.id)

                        // 4. Update Repository and UI
                        val updatedList = state.value.mediaItems.filter { it.id != item.id }
                        mediaSessionRepository.setActiveMediaList(updatedList)
                        
                        _uiEvent.emit(MediaViewerUiEvent.ShowToast("Item deleted permanently"))
                        
                        if (updatedList.isEmpty()) {
                            _uiEvent.emit(MediaViewerUiEvent.NavigateBack)
                        } else {
                            val nextIndex = state.value.currentIndex.coerceAtMost(updatedList.size - 1)
                            _state.update { it.copy(currentIndex = nextIndex) }
                        }
                    }
                }
            }
            MediaViewerEvent.OnManualBackup -> {
                val currentItem = state.value.mediaItems.getOrNull(state.value.currentIndex)
                currentItem?.let { item ->
                    viewModelScope.launch {
                        _uiEvent.emit(MediaViewerUiEvent.ShowToast("Queueing for backup..."))
                        mediaDao.setStatusForIds(listOf(item.id), "PENDING", System.currentTimeMillis())
                        SyncManager.forceSync(application, requireWifi = false, requireCharging = false)
                    }
                }
            }
            MediaViewerEvent.OnRestore -> {
                val currentItem = state.value.mediaItems.getOrNull(state.value.currentIndex)
                currentItem?.let { item ->
                    if (item.localAvailability == com.localcloud.photosclient.data.LocalAvailability.LOCAL_AVAILABLE) {
                        viewModelScope.launch { _uiEvent.emit(MediaViewerUiEvent.ShowToast("Already on device")) }
                        return@let
                    }

                    viewModelScope.launch {
                        _uiEvent.emit(MediaViewerUiEvent.ShowToast("Restoring to device..."))
                        try {
                            val remoteId = item.remoteId ?: return@launch
                            val baseUrl = com.localcloud.photosclient.network.ApiClient.BASE_URL
                            val downloadUrl = "${baseUrl}api/media/download/$remoteId"
                            
                            // Ensure it's in cache first
                            val cachedFile = cloudCacheManager.getOrFetchMedia(remoteId, downloadUrl)
                            if (cachedFile != null && cachedFile.exists()) {
                                // Find original path
                                val originalPath = item.uri.path ?: return@launch
                                val targetFile = java.io.File(originalPath)
                                
                                // Ensure parent directory exists
                                targetFile.parentFile?.mkdirs()
                                
                                // Copy from cache to final destination
                                cachedFile.copyTo(targetFile, overwrite = true)
                                
                                // Update Database
                                mediaDao.updateLocalAvailability(item.id, com.localcloud.photosclient.data.LocalAvailability.LOCAL_AVAILABLE)
                                
                                // Update local list
                                val updatedItem = item.copy(localAvailability = com.localcloud.photosclient.data.LocalAvailability.LOCAL_AVAILABLE)
                                val updatedList = state.value.mediaItems.toMutableList().apply {
                                    set(state.value.currentIndex, updatedItem)
                                }
                                
                                _state.update { it.copy(mediaItems = updatedList) }
                                mediaSessionRepository.setActiveMediaList(updatedList)
                                
                                _uiEvent.emit(MediaViewerUiEvent.ShowToast("Restored successfully"))
                            } else {
                                _uiEvent.emit(MediaViewerUiEvent.ShowToast("Failed to download file"))
                            }
                        } catch(e: Exception) {
                            Log.e("MediaViewerViewModel", "Error restoring file", e)
                            _uiEvent.emit(MediaViewerUiEvent.ShowToast("Error restoring file"))
                        }
                    }
                }
            }
        }
    }

    private fun fetchCloudMedia(item: com.localcloud.photosclient.domain.model.MediaItem) {
        val remoteId = item.remoteId ?: return
        
        // Skip if already downloading or completed
        if (_state.value.downloadedMediaMap.containsKey(remoteId)) return
        
        viewModelScope.launch {
            try {
                // Determine base URL from ApiClient
                // Note: The download endpoint in ApiController is @GetMapping("/download/{id}") -> /api/media/download/{id}
                val baseUrl = com.localcloud.photosclient.network.ApiClient.BASE_URL
                val downloadUrl = "${baseUrl}api/media/download/$remoteId"
                
                Log.d("MediaViewerViewModel", "Fetching cloud media $remoteId from $downloadUrl")
                val file = cloudCacheManager.getOrFetchMedia(remoteId, downloadUrl)
                
                if (file != null && file.exists()) {
                    Log.d("MediaViewerViewModel", "Successfully fetched cloud media $remoteId to ${file.absolutePath}")
                    _state.update { 
                        val newMap = it.downloadedMediaMap.toMutableMap()
                        newMap[remoteId] = file.absolutePath
                        it.copy(downloadedMediaMap = newMap)
                    }
                } else {
                    Log.e("MediaViewerViewModel", "Failed to fetch cloud media $remoteId")
                }
            } catch (e: Exception) {
                Log.e("MediaViewerViewModel", "Exception fetching cloud media $remoteId", e)
            }
        }
    }

    companion object {
        const val KEY_START_INDEX = "startIndex"
        private const val KEY_CURRENT_INDEX = "currentIndex"
    }
}
