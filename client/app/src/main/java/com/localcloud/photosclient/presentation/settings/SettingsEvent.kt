package com.localcloud.photosclient.presentation.settings

sealed class SettingsEvent {
    data class ToggleAutoSync(val enabled: Boolean) : SettingsEvent()
    data class ToggleWifiOnly(val enabled: Boolean) : SettingsEvent()
    data class ToggleChargingOnly(val enabled: Boolean) : SettingsEvent()
    data class ToggleBackgroundSync(val enabled: Boolean) : SettingsEvent()
    data class ToggleAllowLocalRemoval(val enabled: Boolean) : SettingsEvent()
    data class ToggleDebugInfo(val enabled: Boolean) : SettingsEvent()
}
