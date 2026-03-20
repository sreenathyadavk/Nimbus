package com.localcloud.photosclient.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getApplicationContext() = context

    private val dataStore = context.dataStore

    companion object {
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val SYNC_WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        val SYNC_ON_CHARGING_ONLY = booleanPreferencesKey("sync_on_charging_only")
        val BACKGROUND_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        val ALLOW_LOCAL_REMOVAL = booleanPreferencesKey("allow_local_removal")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val SHOW_DEBUG_INFO = booleanPreferencesKey("show_debug_info")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
    }

    val autoSyncEnabled: Flow<Boolean> = dataStore.data.map { it[AUTO_SYNC_ENABLED] ?: false }
    val syncWifiOnly: Flow<Boolean> = dataStore.data.map { it[SYNC_WIFI_ONLY] ?: true }
    val syncOnChargingOnly: Flow<Boolean> = dataStore.data.map { it[SYNC_ON_CHARGING_ONLY] ?: false }
    val backgroundSyncEnabled: Flow<Boolean> = dataStore.data.map { it[BACKGROUND_SYNC_ENABLED] ?: false }
    val allowLocalRemoval: Flow<Boolean> = dataStore.data.map { it[ALLOW_LOCAL_REMOVAL] ?: false }
    val showDebugInfo: Flow<Boolean> = dataStore.data.map { it[SHOW_DEBUG_INFO] ?: false }
    val lastSyncTime: Flow<Long> = dataStore.data.map { it[LAST_SYNC_TIME] ?: 0L }
    
    val deviceId: Flow<String> = dataStore.data.map { preferences ->
        preferences[DEVICE_ID] ?: run {
            // DataStore handles atomic updates internally, but we initially emit a fallback
            // We will write a UUID if missing in a separate coroutine call or on init
            ""
        }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[AUTO_SYNC_ENABLED] = enabled }
    }

    suspend fun setSyncWifiOnly(enabled: Boolean) {
        dataStore.edit { it[SYNC_WIFI_ONLY] = enabled }
    }

    suspend fun setSyncOnChargingOnly(enabled: Boolean) {
        dataStore.edit { it[SYNC_ON_CHARGING_ONLY] = enabled }
    }

    suspend fun setBackgroundSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[BACKGROUND_SYNC_ENABLED] = enabled }
    }

    suspend fun setAllowLocalRemoval(enabled: Boolean) {
        dataStore.edit { it[ALLOW_LOCAL_REMOVAL] = enabled }
    }

    suspend fun setShowDebugInfo(enabled: Boolean) {
        dataStore.edit { it[SHOW_DEBUG_INFO] = enabled }
    }

    suspend fun updateLastSyncTime(timestamp: Long) {
        dataStore.edit { it[LAST_SYNC_TIME] = timestamp }
    }

    suspend fun getOrGenerateDeviceId(): String {
        return context.dataStore.edit { preferences ->
            if (preferences[DEVICE_ID] == null) {
                preferences[DEVICE_ID] = UUID.randomUUID().toString()
            }
        }[DEVICE_ID]!!
    }
}
