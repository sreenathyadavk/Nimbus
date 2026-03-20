package com.localcloud.photosclient.presentation.settings

import com.localcloud.photosclient.domain.model.SyncSettings

data class SettingsState(
    val syncSettings: SyncSettings = SyncSettings(
        autoSyncEnabled = false,
        wifiOnly = true,
        chargingOnly = false,
        backgroundSyncEnabled = false
    ),
    val deviceId: String = "",
    val appVersion: String = "",
    val debugEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val lastSyncTime: String = "Never",
    val totalSyncedCount: Int = 0,
    val totalMediaCount: Int = 0,
    val databaseSize: String = "0 KB",
    val allowLocalRemoval: Boolean = false
)
