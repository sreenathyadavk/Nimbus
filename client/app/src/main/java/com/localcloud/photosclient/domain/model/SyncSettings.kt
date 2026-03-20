package com.localcloud.photosclient.domain.model

data class SyncSettings(
    val autoSyncEnabled: Boolean,
    val wifiOnly: Boolean,
    val chargingOnly: Boolean,
    val backgroundSyncEnabled: Boolean
)
