package com.localcloud.photosclient.data.repository

import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthState {
    object Authenticated : AuthState()
    data class NotAuthorized(val deviceId: String) : AuthState()
    data class Error(val message: String) : AuthState()
    object Loading : AuthState()
}

@Singleton
class DeviceAuthManager @Inject constructor(
    private val authRepository: AuthRepository
) {
    /**
     * Ensures the device is authenticated. Call on every app launch.
     *
     * Flow:
     * 1. If we have a refresh token → try /auth/refresh
     *    - success → Authenticated
     * 2. If no refresh token or refresh failed → call registerDevice()
     *    - success → Authenticated
     *    - 403 → NotAuthorized (device not in allowed_devices)
     *    - other error → Error
     */
    suspend fun ensureAuthenticated(): AuthState {
        // Step 1: Try refreshing existing tokens
        if (authRepository.hasTokens()) {
            val refreshResult = authRepository.refreshToken()
            if (refreshResult.isSuccess) {
                return AuthState.Authenticated
            }
            // Refresh failed — fall through to re-register
        }

        // Step 2: Register device (or re-register)
        val registerResult = authRepository.registerDevice()
        return if (registerResult.isSuccess) {
            AuthState.Authenticated
        } else {
            val exception = registerResult.exceptionOrNull()
            if (exception is DeviceNotAuthorizedException) {
                AuthState.NotAuthorized(exception.deviceId)
            } else {
                AuthState.Error(exception?.message ?: "Unknown authentication error")
            }
        }
    }
}
