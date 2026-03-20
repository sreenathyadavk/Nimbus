package com.localcloud.photosclient.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.Instant

class TokenDataStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit().apply {
            putString("access_token", accessToken)
            putString("refresh_token", refreshToken)
            apply()
        }
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun clearTokens() {
        prefs.edit().apply {
            remove("access_token")
            remove("refresh_token")
            apply()
        }
    }

    fun saveLastSyncAt(instant: Instant) {
        prefs.edit().putLong("last_sync_at", instant.toEpochMilli()).apply()
    }

    fun getLastSyncAt(): Instant {
        val millis = prefs.getLong("last_sync_at", 0L)
        return Instant.ofEpochMilli(millis)
    }
}
