package com.localcloud.photosclient.data.repository

import com.localcloud.photosclient.data.preferences.TokenDataStore
import com.localcloud.photosclient.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenDataStore: TokenDataStore
) {
    suspend fun login(request: LoginRequest): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.login(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    tokenDataStore.saveTokens(body.accessToken, body.refreshToken)
                    Result.success(body)
                } else {
                    Result.failure(Exception("Empty login response"))
                }
            } else {
                Result.failure(Exception("Login failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refresh(refreshToken: String): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.refresh(RefreshRequest(refreshToken))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    tokenDataStore.saveTokens(body.accessToken, body.refreshToken)
                    Result.success(body)
                } else {
                    Result.failure(Exception("Empty refresh response"))
                }
            } else {
                Result.failure(Exception("Refresh failed: ${response.message()}"))
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

    fun isLoggedIn(): Boolean {
        return tokenDataStore.getAccessToken() != null
    }
}
