package com.localcloud.photosclient.network

import com.localcloud.photosclient.data.preferences.TokenDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import android.util.Log
import android.os.Build

class AuthInterceptor(
    private val tokenDataStore: TokenDataStore,
    private val apiService: dagger.Lazy<ApiService>
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenDataStore.getAccessToken()

        val requestBuilder = originalRequest.newBuilder()
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        // Handle 401 Unauthorized OR 403 Forbidden
        if (response.code == 401 || response.code == 403) {
            synchronized(this) {
                // Check if another thread already refreshed
                val currentToken = tokenDataStore.getAccessToken()
                val requestToken = originalRequest.header("Authorization")?.substringAfter("Bearer ")

                if (currentToken != null && currentToken != requestToken) {
                    response.close()
                    return chain.proceed(originalRequest.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build())
                }

                Log.d("AUTH_DEBUG", "Caught ${response.code}. Attempting silent recovery...")
                
                val success = runBlocking {
                    try {
                        // 1. Try Refresh first
                        val refreshToken = tokenDataStore.getRefreshToken()
                        var refreshSuccess = false
                        if (refreshToken != null) {
                            val refreshRes = apiService.get().refresh(RefreshRequest(refreshToken))
                            if (refreshRes.isSuccessful) {
                                refreshRes.body()?.let {
                                    Log.d("AUTH_DEBUG", "Refresh successful")
                                    tokenDataStore.saveTokens(it.accessToken, it.refreshToken)
                                    refreshSuccess = true
                                }
                            } else {
                                Log.d("AUTH_DEBUG", "Refresh call failed with ${refreshRes.code()}")
                            }
                        }

                        if (!refreshSuccess) {
                            // 2. Refresh failed or no refresh token -> Full Re-registration
                            Log.d("AUTH_DEBUG", "Refresh failed or unavailable. Re-registering device...")
                            val deviceId = tokenDataStore.getDeviceId() ?: java.util.UUID.randomUUID().toString().also { tokenDataStore.saveDeviceId(it) }
                            val deviceName = Build.MODEL
                            
                            val regRes = apiService.get().registerDevice(DeviceRegistrationRequest(deviceId, deviceName))
                            if (regRes.isSuccessful) {
                                regRes.body()?.let {
                                    Log.d("AUTH_DEBUG", "Re-registration successful")
                                    tokenDataStore.saveTokens(it.accessToken, it.refreshToken)
                                    true
                                } ?: false
                            } else {
                                Log.e("AUTH_DEBUG", "Re-registration failed with ${regRes.code()}")
                                false
                            }
                        } else true
                    } catch (e: Exception) {
                        Log.e("AUTH_DEBUG", "Recovery failed with exception", e)
                        false
                    }
                }

                if (success) {
                    val newToken = tokenDataStore.getAccessToken()
                    response.close()
                    return chain.proceed(originalRequest.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build())
                }
            }
        }

        return response
    }
}

class TokenAuthenticator(
    private val tokenDataStore: TokenDataStore,
    private val apiService: dagger.Lazy<ApiService>
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Authenticator only handles 401. Since we handle both 401 and 403 in Interceptor with retry,
        // we can return null here as the Interceptor will have already handled the recovery.
        return null
    }
}
