package com.localcloud.photosclient.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localcloud.photosclient.BuildConfig
import com.localcloud.photosclient.data.preferences.SettingsDataStore
import com.localcloud.photosclient.domain.model.SyncSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: SettingsDataStore
) : ViewModel() {

    // Ensure DEVICE_ID is generated at least once when Settings is opened
    init {
        viewModelScope.launch {
            dataStore.getOrGenerateDeviceId()
        }
    }

    private val mediaDao = com.localcloud.photosclient.data.AppDatabase.getDatabase(dataStore.getApplicationContext()).mediaDao()

    val state: StateFlow<SettingsState> = combine(
        dataStore.autoSyncEnabled,
        dataStore.syncWifiOnly,
        dataStore.syncOnChargingOnly,
        dataStore.backgroundSyncEnabled,
        dataStore.deviceId,
        dataStore.showDebugInfo,
        dataStore.lastSyncTime,
        dataStore.allowLocalRemoval,
        mediaDao.getAllMediaFlow()
    ) { values ->
        val autoSync = values[0] as Boolean
        val wifiOnly = values[1] as Boolean
        val chargingOnly = values[2] as Boolean
        val backSync = values[3] as Boolean
        val dId = values[4] as String
        val showDebug = values[5] as Boolean
        val lastSync = values[6] as Long
        val allowRemoval = values[7] as Boolean
        @Suppress("UNCHECKED_CAST")
        val mediaList = values[8] as List<com.localcloud.photosclient.data.LocalMedia>

        val formattedLastSync = if (lastSync == 0L) "Never" else {
            java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSync))
        }

        SettingsState(
            syncSettings = SyncSettings(
                autoSyncEnabled = autoSync,
                wifiOnly = wifiOnly,
                chargingOnly = chargingOnly,
                backgroundSyncEnabled = backSync
            ),
            deviceId = dId,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            debugEnabled = showDebug,
            lastSyncTime = formattedLastSync,
            totalSyncedCount = mediaList.count { it.uploadStatus == "SUCCESS" },
            totalMediaCount = mediaList.size,
            databaseSize = try {
                val dbFile = dataStore.getApplicationContext().getDatabasePath("photos_database")
                if (dbFile.exists()) {
                    val sizeInKb = dbFile.length() / 1024
                    if (sizeInKb > 1024) {
                        String.format("%.2f MB", sizeInKb / 1024.0)
                    } else {
                        "$sizeInKb KB"
                    }
                } else "0 KB"
            } catch (e: Exception) { "0 KB" },
            allowLocalRemoval = allowRemoval,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsState(isLoading = true)
    )

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.ToggleAutoSync -> dataStore.setAutoSyncEnabled(event.enabled)
                is SettingsEvent.ToggleWifiOnly -> dataStore.setSyncWifiOnly(event.enabled)
                is SettingsEvent.ToggleChargingOnly -> dataStore.setSyncOnChargingOnly(event.enabled)
                is SettingsEvent.ToggleBackgroundSync -> dataStore.setBackgroundSyncEnabled(event.enabled)
                is SettingsEvent.ToggleAllowLocalRemoval -> dataStore.setAllowLocalRemoval(event.enabled)
                is SettingsEvent.ToggleDebugInfo -> dataStore.setShowDebugInfo(event.enabled)
            }
        }
    }
}
