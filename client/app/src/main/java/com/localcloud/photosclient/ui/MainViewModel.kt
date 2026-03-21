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
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.localcloud.photosclient.presentation.home.HomeSelectionState
import com.localcloud.photosclient.presentation.home.HomeSelectionEvent
import com.localcloud.photosclient.sync.SyncManager
import com.localcloud.photosclient.data.preferences.SettingsDataStore
import java.io.File
import javax.inject.Inject
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat

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

    val favoritesFlow: StateFlow<List<LocalMedia>> = mediaFlow.map { list ->
        list.filter { it.isFavorite && !it.isDeleted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashFlow: StateFlow<List<LocalMedia>> = mediaFlow.map { list ->
        list.filter { it.isDeleted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class TimelineGroup(val title: String, val items: List<LocalMedia>)
    
    val timelineFlow: StateFlow<List<TimelineGroup>> = mediaFlow.map { list ->
        val sorted = list.filter { !it.isDeleted }.sortedByDescending { it.dateAdded }
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

    private val _activeViewerList = MutableStateFlow<List<LocalMedia>>(emptyList())
    val activeViewerList: StateFlow<List<LocalMedia>> = _activeViewerList.asStateFlow()

    fun setActiveViewerList(list: List<LocalMedia>) {
        Log.d("NAV_DEBUG", "MainViewModel: Setting activeViewerList size=${list.size}")
        _activeViewerList.value = list
    }

    private val _selectionState = MutableStateFlow(HomeSelectionState())
    val selectionState: StateFlow<HomeSelectionState> = _selectionState.asStateFlow()

    private fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

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
                    state.copy(selectedItems = newSelection, isSelectionMode = newSelection.isNotEmpty())
                }
            }
            is HomeSelectionEvent.ClearSelection -> {
                _selectionState.update { it.copy(isSelectionMode = false, selectedItems = emptySet()) }
            }
        }
    }

    fun refreshMedia() {
        viewModelScope.launch {
            mediaStoreManager.scanAndSyncMediaStore()
        }
    }

    val syncStatsFlow: StateFlow<SyncStats> = mediaFlow.map { list ->
        SyncStats(
            total = list.size,
            synced = list.count { it.uploadStatus == "SUCCESS" },
            pending = list.count { it.uploadStatus == "PENDING" },
            uploading = list.count { it.uploadStatus == "UPLOADING" },
            failed = list.count { it.uploadStatus == "FAILED" }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStats())

    fun triggerGlobalManualBackup() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val wifiOnly = settingsDataStore.syncWifiOnly.first()
                val chargingOnly = settingsDataStore.syncOnChargingOnly.first()
                val count = mediaDao.queueAllUnsynced(System.currentTimeMillis())
                if (count > 0) {
                    SyncManager.forceSync(getApplication(), requireWifi = wifiOnly, requireCharging = chargingOnly)
                }
            }
        }
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
}
