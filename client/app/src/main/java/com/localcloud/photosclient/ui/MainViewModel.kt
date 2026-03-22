package com.localcloud.photosclient.ui

import android.app.Application
import android.app.RecoverableSecurityException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localcloud.photosclient.data.AppDatabase
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.sync.MediaStoreManager
import com.localcloud.photosclient.sync.MediaStoreObserver
import com.localcloud.photosclient.domain.model.MediaItem
import com.localcloud.photosclient.domain.model.MediaType
import com.localcloud.photosclient.domain.repository.MediaSessionRepository
import android.content.ContentUris
import android.provider.MediaStore
import android.content.IntentSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.localcloud.photosclient.presentation.home.HomeSelectionState
import com.localcloud.photosclient.presentation.home.HomeSelectionEvent
import com.localcloud.photosclient.sync.SyncManager
import com.localcloud.photosclient.data.preferences.SettingsDataStore
import java.io.File
import javax.inject.Inject
import android.net.Uri
import android.util.Log
import com.localcloud.photosclient.sync.SyncScheduler
import com.localcloud.photosclient.data.UploadStatus
import androidx.work.*
import com.localcloud.photosclient.sync.AutoDeleteWorker
import java.util.concurrent.TimeUnit

data class SyncStats(
    val total: Int = 0, 
    val synced: Int = 0, 
    val pending: Int = 0, 
    val uploading: Int = 0,
    val failed: Int = 0,
    val overallProgressPercentage: Int = 0
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val sessionRepository: MediaSessionRepository,
    private val settingsDataStore: SettingsDataStore,
    private val syncScheduler: SyncScheduler
) : AndroidViewModel(application) {

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val mediaDao = AppDatabase.getDatabase(application).mediaDao()
    val mediaStoreManager = MediaStoreManager(application, settingsDataStore)
    private val mediaStoreObserver = MediaStoreObserver(application, viewModelScope, mediaStoreManager)

    private val _pendingDeleteIntent = MutableSharedFlow<IntentSender>()
    val pendingDeleteIntent = _pendingDeleteIntent.asSharedFlow()

    private val _deletedMediaRefresh = MutableStateFlow(0L)

    private val _activeViewerList = MutableStateFlow<List<LocalMedia>>(emptyList())
    val activeViewerList: StateFlow<List<MediaItem>> = combine(_activeViewerList, mediaDao.getAllMediaFlow()) { active, allMedia ->
        val activeIds = active.map { it.id }.toSet()
        // Filter active list to only show non-deleted items
        // We look at allMedia to get the latest deleted state
        val filtered = allMedia.filter { it.id in activeIds && !it.isDeleted }
        filtered.map { localToMediaItem(it) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // 1. Start active monitoring for real-time changes
        mediaStoreObserver.startObserving()

        viewModelScope.launch {
            mediaStoreManager.scanAndSyncMediaStore()
        }

        // Schedule auto-delete after 30 days
        val autoDeleteRequest = PeriodicWorkRequestBuilder<AutoDeleteWorker>(
            1, TimeUnit.DAYS
        ).build()
        
        WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            "auto_delete_expired",
            ExistingPeriodicWorkPolicy.KEEP,
            autoDeleteRequest
        )
    }

    override fun onCleared() {
        mediaStoreObserver.stopObserving()
        super.onCleared()
    }

    val mediaFlow: StateFlow<List<LocalMedia>> = mediaDao.getAllMediaFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val timelineItems: StateFlow<List<LocalMedia>> = mediaFlow
        .map { list -> list.filter { !it.isDeleted } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favorites: StateFlow<List<LocalMedia>> = mediaDao.getFavorites()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val deletedMedia: StateFlow<List<LocalMedia>> = combine(mediaDao.getDeleted(), _deletedMediaRefresh) { list, _ ->
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun refreshDeletedMedia() {
        _deletedMediaRefresh.value = System.currentTimeMillis()
    }

    val albumsFlow: StateFlow<List<com.localcloud.photosclient.data.AlbumStats>> = mediaDao.getAlbumsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    data class TimelineGroup(val title: String, val items: List<LocalMedia>)
    
    private val _gridColumns = MutableStateFlow(3)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    fun updateGridColumns(delta: Int) {
        _gridColumns.update { (it + delta).coerceIn(2, 4) }
    }

    val syncStatsFlow: StateFlow<SyncStats> = mediaFlow.map { list ->
        val total = list.size
        val synced = list.count { it.uploadStatus == "SUCCESS" }
        val pending = list.count { it.uploadStatus == "PENDING" }
        val uploading = list.count { it.uploadStatus == "UPLOADING" }
        val failed = list.count { it.uploadStatus == "FAILED" }
        
        val uploadingFiles = list.filter { it.uploadStatus == "UPLOADING" }
        var avgProgress = 0
        if (uploadingFiles.isNotEmpty()) {
            var totalBytes = 0L
            var uploadedBytes = 0L
            for (file in uploadingFiles) {
                val size = File(file.path).length()
                totalBytes += size
                uploadedBytes += (size * (file.progress / 100.0)).toLong()
            }
            if (totalBytes > 0) {
                avgProgress = ((uploadedBytes.toDouble() / totalBytes) * 100).toInt()
            }
        }

        SyncStats(
            total = total,
            synced = synced,
            pending = pending,
            uploading = uploading,
            failed = failed,
            overallProgressPercentage = avgProgress
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncStats()
    )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun refreshMedia() {
        viewModelScope.launch {
            _isSyncing.value = true
            mediaStoreManager.scanAndSyncMediaStore()
            _isSyncing.value = false
        }
    }

    fun getMediaByFolder(folderPath: String): Flow<List<LocalMedia>> {
        return mediaFlow.map { list ->
            list.filter { File(it.path).parentFile?.absolutePath == folderPath }
        }
    }

    private val _selectionState = MutableStateFlow(HomeSelectionState())
    val selectionState: StateFlow<HomeSelectionState> = _selectionState.asStateFlow()

    fun onSelectionEvent(event: HomeSelectionEvent) {
        when (event) {
            is HomeSelectionEvent.LongPress -> {
                _selectionState.update { 
                    it.copy(
                        isSelectionMode = true,
                        selectedItems = it.selectedItems + event.media.id
                    ) 
                }
            }
            is HomeSelectionEvent.ToggleSelection -> {
                _selectionState.update { state ->
                    if (!state.isSelectionMode) return@update state
                    
                    val newSelection = if (state.selectedItems.contains(event.media.id)) {
                        state.selectedItems - event.media.id
                    } else {
                        state.selectedItems + event.media.id
                    }
                    
                    state.copy(
                        selectedItems = newSelection,
                        isSelectionMode = newSelection.isNotEmpty()
                    )
                }
            }
            is HomeSelectionEvent.ClearSelection -> {
                _selectionState.update { 
                    it.copy(
                        isSelectionMode = false,
                        selectedItems = emptySet()
                    ) 
                }
            }
        }
    }

    fun triggerManualSync() {
        refreshMedia()
    }

    fun triggerGlobalManualBackup() {
        viewModelScope.launch {
            _isSyncing.value = true
            withContext(Dispatchers.IO) {
                val wifiOnly = settingsDataStore.syncWifiOnly.first()
                val chargingOnly = settingsDataStore.syncOnChargingOnly.first()
                
                val count = mediaDao.queueAllUnsynced(System.currentTimeMillis())
                if (count > 0) {
                    _uiEvent.emit("Backing up $count items...")
                    SyncManager.forceSync(getApplication(), requireWifi = wifiOnly, requireCharging = chargingOnly)
                    settingsDataStore.updateLastSyncTime(System.currentTimeMillis())
                } else {
                    _uiEvent.emit("All items already backed up")
                }
            }
            _isSyncing.value = false
        }
    }

    fun triggerManualSyncForSelectedItems() {
        viewModelScope.launch {
            val selectedIds = _selectionState.value.selectedItems
            if (selectedIds.isEmpty()) return@launch

            _isSyncing.value = true
            withContext(Dispatchers.IO) {
                val allMedia = mediaFlow.value
                val unsyncedSelectedIds = allMedia
                    .filter { it.id in selectedIds && it.uploadStatus != "SUCCESS" }
                    .map { it.id }

                if (unsyncedSelectedIds.isNotEmpty()) {
                    _uiEvent.emit("Backing up ${unsyncedSelectedIds.size} selected items...")
                    mediaDao.setStatusForIds(unsyncedSelectedIds, "PENDING", System.currentTimeMillis())
                    val wifiOnly = settingsDataStore.syncWifiOnly.first()
                    val chargingOnly = settingsDataStore.syncOnChargingOnly.first()
                    SyncManager.forceSync(getApplication(), requireWifi = wifiOnly, requireCharging = chargingOnly)
                    settingsDataStore.updateLastSyncTime(System.currentTimeMillis())
                } else {
                    _uiEvent.emit("Selected items already backed up")
                }
            }
            
            _isSyncing.value = false
            onSelectionEvent(HomeSelectionEvent.ClearSelection)
        }
    }
    
    fun setActiveViewerList(allMedia: List<LocalMedia>) {
        if (allMedia.isNotEmpty()) {
            _activeViewerList.value = allMedia
        }
    }

    fun triggerUploadForItem(media: LocalMedia) {
        viewModelScope.launch {
            // Persistence
            mediaDao.updateUploadStatus(media.id, UploadStatus.PENDING.name)
            syncScheduler.triggerImmediateSync()
        }
    }

    fun onMediaClick(unusedMedia: LocalMedia, allMedia: List<LocalMedia>) {
        _activeViewerList.value = allMedia
    }

    fun retryFailedUpload(mediaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            mediaDao.updateStatus(mediaId, "PENDING", 0, System.currentTimeMillis())
            mediaDao.updateProgress(mediaId, 0)
            
            val wifiOnly = settingsDataStore.syncWifiOnly.first()
            val chargingOnly = settingsDataStore.syncOnChargingOnly.first()
            SyncManager.forceSync(getApplication(), requireWifi = wifiOnly, requireCharging = chargingOnly)
        }
    }

    fun toggleFavorite(media: LocalMedia) {
        viewModelScope.launch {
            val nextValue = !media.isFavorite
            // Persistence
            mediaDao.setFavorite(media.id, nextValue)
        }
    }

    fun clearSelection() {
        onSelectionEvent(HomeSelectionEvent.ClearSelection)
    }

    fun deleteMedia(media: LocalMedia) {
        viewModelScope.launch {
            mediaDao.updateMedia(media.copy(isDeleted = true, deletedAt = System.currentTimeMillis()))
        }
    }

    fun restoreMedia(media: LocalMedia) {
        viewModelScope.launch {
            mediaDao.updateMedia(media.copy(isDeleted = false, deletedAt = null))
        }
    }

    fun permanentlyDelete(media: LocalMedia) {
        viewModelScope.launch {
            // 1. Try to delete physical file
            try {
                val uri = Uri.parse(media.localUri)
                getApplication<Application>().contentResolver.delete(uri, null, null)
            } catch (e: RecoverableSecurityException) {
                _pendingDeleteIntent.emit(e.userAction.actionIntent.intentSender)
            } catch (e: Exception) {
                Log.w("ViewModel", "File delete failed: ${e.message}")
                // Continue anyway — always remove from DB
            }
            
            // 2. ALWAYS remove from Room DB regardless
            mediaDao.permanentlyDelete(media.id)
            Log.d("ViewModel", "Permanently deleted ID: ${media.id}")
            refreshDeletedMedia()
        }
    }

    fun permanentlyDeleteBatch(items: List<LocalMedia>) {
        if (items.isEmpty()) return
        
        viewModelScope.launch {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // For Android 11+, we still use createDeleteRequest because it's better UX
                try {
                    val uris = items.map { Uri.parse(it.localUri) }
                    val pendingIntent = MediaStore.createDeleteRequest(
                        getApplication<Application>().contentResolver,
                        uris
                    )
                    _pendingDeleteIntent.emit(pendingIntent.intentSender)
                    
                    // For UI consistency as per user request, we remove from Room immediately 
                    // or at least clear them so they don't show up.
                    items.forEach { mediaDao.permanentlyDelete(it.id) }
                } catch (e: Exception) {
                    Log.e("ViewModel", "Batch delete failed", e)
                    items.forEach { mediaDao.permanentlyDelete(it.id) }
                }
            } else {
                items.forEach { permanentlyDelete(it) }
            }
            refreshDeletedMedia()
        }
    }

    private fun localToMediaItem(local: LocalMedia): MediaItem {
        val uri = if (local.mediaType.startsWith("video")) {
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, local.id)
        } else {
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, local.id)
        }
        return MediaItem(
            id = local.id,
            uri = uri,
            type = if (local.mediaType.startsWith("video")) MediaType.VIDEO else MediaType.IMAGE,
            displayName = File(local.path).name,
            size = File(local.path).length(),
            dateModified = local.dateAdded,
            width = local.width,
            height = local.height,
            duration = local.duration,
            remoteId = local.remoteId,
            uploadStatus = local.uploadStatus,
            localAvailability = local.localAvailability,
            isFavorite = local.isFavorite,
            isDeleted = local.isDeleted
        )
    }

    private fun getMediaLocalUri(item: LocalMedia): String {
        val baseUri = if (item.mediaType.startsWith("video", ignoreCase = true)) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(baseUri, item.id).toString()
    }
}
