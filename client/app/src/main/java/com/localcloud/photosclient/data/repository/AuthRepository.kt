package com.localcloud.photosclient.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.localcloud.photosclient.data.preferences.TokenDataStore
import com.localcloud.photosclient.network.ApiService
import com.localcloud.photosclient.network.DeviceRegistrationRequest
import com.localcloud.photosclient.network.RefreshRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

class DeviceNotAuthorizedException(val deviceId: String) : Exception("Device not authorized: $deviceId")

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenDataStore: TokenDataStore,
    @ApplicationContext private val context: Context
) {
    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    suspend fun registerDevice(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deviceId = getDeviceId()
            val deviceName = getDeviceName()
            val response = apiService.registerDevice(
                DeviceRegistrationRequest(deviceId, deviceName)
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    tokenDataStore.saveTokens(body.accessToken, body.refreshToken)
                    tokenDataStore.saveDeviceId(deviceId)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Empty registration response"))
                }
            } else if (response.code() == 403) {
                Result.failure(DeviceNotAuthorizedException(deviceId))
            } else {
                Result.failure(Exception("Registration failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            if (e is DeviceNotAuthorizedException) Result.failure(e)
            else Result.failure(e)
        }
    }

    suspend fun refreshToken(): Result<Unit> = withContext(Dispatchers.IO) {
        val refreshToken = tokenDataStore.getRefreshToken()
            ?: return@withContext Result.failure(Exception("No refresh token"))
        try {
            val response = apiService.refresh(RefreshRequest(refreshToken))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    tokenDataStore.saveTokens(body.accessToken, body.refreshToken)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Empty refresh response"))
                }
            } else {
                Result.failure(Exception("Refresh failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val refreshToken = tokenDataStore.getRefreshToken()
        if (refreshToken != null) {
            try {
                apiService.logout(RefreshRequest(refreshToken))
            } catch (e: Exception) {
                // Ignore logout call failure, continue to clear local tokens
            }
        }
        tokenDataStore.clearTokens()
    }

    fun hasTokens(): Boolean {
        return tokenDataStore.getAccessToken() != null
    }
}
