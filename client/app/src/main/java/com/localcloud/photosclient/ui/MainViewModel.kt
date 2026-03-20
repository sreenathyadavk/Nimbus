package com.localcloud.photosclient.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localcloud.photosclient.data.AppDatabase
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.sync.MediaStoreManager
import com.localcloud.photosclient.domain.model.MediaItem
import com.localcloud.photosclient.domain.model.MediaType
import com.localcloud.photosclient.domain.repository.MediaSessionRepository
import android.content.ContentUris
import android.provider.MediaStore
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
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat

data class Album(val name: String, val folderPath: String, val coverPath: String, val count: Int)

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
    private val settingsDataStore: SettingsDataStore
) : AndroidViewModel(application) {

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val mediaDao = AppDatabase.getDatabase(application).mediaDao()
    val mediaStoreManager = MediaStoreManager(application, settingsDataStore)

    val mediaFlow: StateFlow<List<LocalMedia>> = mediaDao.getAllMediaFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val albumFlow: StateFlow<List<Album>> = mediaFlow.map { mediaList ->
        mediaList.groupBy { File(it.path).parentFile?.absolutePath ?: "Unknown" }
            .map { (path, list) ->
                Album(
                    name = File(path).name,
                    folderPath = path,
                    coverPath = list.firstOrNull()?.path ?: "",
                    count = list.size
                )
            }
            .sortedByDescending { it.count }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    data class TimelineGroup(val title: String, val items: List<LocalMedia>)
    
    val photosFlow: StateFlow<List<TimelineGroup>> = mediaFlow.map { list ->
        val sorted = list.sortedByDescending { it.dateAdded }
        val groups = mutableListOf<TimelineGroup>()
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val monthlyGroups = mutableMapOf<String, MutableList<LocalMedia>>()

        sorted.forEach { item ->
            val key = monthFormat.format(java.util.Date(item.dateAdded))
            monthlyGroups.getOrPut(key) { mutableListOf() }.add(item)
        }

        monthlyGroups.entries
            .sortedByDescending { it.value.first().dateAdded }
            .forEach { (title, items) ->
                groups.add(TimelineGroup(title, items))
            }

        groups
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val timelineFlow: StateFlow<List<TimelineGroup>> = mediaFlow.map { list ->
        val sorted = list.sortedByDescending { it.dateAdded }
        val groups = mutableListOf<TimelineGroup>()
        
        val now = System.currentTimeMillis()
        val todayStart = getStartOfDay(now)
        val yesterdayStart = todayStart - 86400000
        
        val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

        val todayItems = mutableListOf<LocalMedia>()
        val yesterdayItems = mutableListOf<LocalMedia>()
        val dateGroups = mutableMapOf<String, MutableList<LocalMedia>>()

        sorted.forEach { item ->
            val timestamp = item.dateAdded
            when {
                timestamp >= todayStart -> todayItems.add(item)
                timestamp >= yesterdayStart -> yesterdayItems.add(item)
                else -> {
                    val key = dateFormat.format(java.util.Date(timestamp))
                    dateGroups.getOrPut(key) { mutableListOf() }.add(item)
                }
            }
        }

        if (todayItems.isNotEmpty()) groups.add(TimelineGroup("Today", todayItems))
        if (yesterdayItems.isNotEmpty()) groups.add(TimelineGroup("Yesterday", yesterdayItems))
        
        // Sort remaining dates by reverse chronological order
        dateGroups.entries
            .sortedByDescending { it.value.first().dateAdded }
            .forEach { (title, items) ->
                groups.add(TimelineGroup(title, items))
            }

        groups
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }



    val syncStatsFlow: StateFlow<SyncStats> = mediaFlow.map { list ->
        val total = list.size
        val synced = list.count { it.uploadStatus == "SUCCESS" }
        val pending = list.count { it.uploadStatus == "PENDING" }
        val uploading = list.count { it.uploadStatus == "UPLOADING" }
        val failed = list.count { it.uploadStatus == "FAILED" }
        
        // Calculate overall progress across files that are currently uploading (not fully synced)
        // If nothing is uploading, progress is 0. 
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
                android.util.Log.d("SYNC_DEBUG", "Manual backup triggered")
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
            android.util.Log.d("SYNC_DEBUG", "MainViewModel: triggerManualSyncForSelectedItems called for IDs: $selectedIds")
            if (selectedIds.isEmpty()) return@launch

            _isSyncing.value = true
            withContext(Dispatchers.IO) {
                // Ensure we only queue items that are not already SYNCED ("SUCCESS")
                val allMedia = mediaFlow.value
                val unsyncedSelectedIds = allMedia
                    .filter { it.id in selectedIds && it.uploadStatus != "SUCCESS" }
                    .map { it.id }

                if (unsyncedSelectedIds.isNotEmpty()) {
                    android.util.Log.d("SYNC_DEBUG", "MainViewModel: Setting status to PENDING for IDs: $unsyncedSelectedIds")
                    _uiEvent.emit("Backing up ${unsyncedSelectedIds.size} selected items...")
                    mediaDao.setStatusForIds(unsyncedSelectedIds, "PENDING", System.currentTimeMillis())
                    val wifiOnly = settingsDataStore.syncWifiOnly.first()
                    val chargingOnly = settingsDataStore.syncOnChargingOnly.first()
                    android.util.Log.d("SYNC_DEBUG", "MainViewModel: Forcing SyncManager execution")
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

    fun onMediaClick(media: LocalMedia, allMedia: List<LocalMedia>) {
        android.util.Log.d("MainViewModel", "onMediaClick: Mapping ${allMedia.size} items to MediaItem")
        val mappedList = allMedia.map { local ->
            val uri = if (local.mediaType.startsWith("video")) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, local.id)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, local.id)
            }
            MediaItem(
                id = local.id,
                uri = uri,
                type = if (local.mediaType.startsWith("video")) MediaType.VIDEO else MediaType.IMAGE,
                displayName = File(local.path).name,
                size = File(local.path).length(),
                dateModified = local.dateAdded, // Fallback since phase 1 used whatever DB had
                width = 0, // Fallback for memory scaler debug logs
                height = 0, // Fallback for memory scaler debug logs
                duration = local.duration,
                remoteId = local.remoteId,
                localAvailability = local.localAvailability
            )
        }
        sessionRepository.setActiveMediaList(mappedList)
    }

    fun retryFailedUpload(mediaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            // Reset status to PENDING and progress to 0
            mediaDao.updateStatus(mediaId, "PENDING", 0, System.currentTimeMillis())
            mediaDao.updateProgress(mediaId, 0)
            
            val wifiOnly = settingsDataStore.syncWifiOnly.first()
            val chargingOnly = settingsDataStore.syncOnChargingOnly.first()
            SyncManager.forceSync(getApplication(), requireWifi = wifiOnly, requireCharging = chargingOnly)
        }
    }
}
