package com.localcloud.photosclient.network

import com.localcloud.photosclient.data.preferences.TokenDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class AuthInterceptor(private val tokenDataStore: TokenDataStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenDataStore.getAccessToken()

        val requestBuilder = originalRequest.newBuilder()
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}

class TokenAuthenticator(
    private val tokenDataStore: TokenDataStore,
    private val apiService: Lazy<ApiService>
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val refreshToken = tokenDataStore.getRefreshToken() ?: return null

        synchronized(this) {
            val currentToken = tokenDataStore.getAccessToken()
            val requestToken = response.request.header("Authorization")?.substringAfter("Bearer ")

            // If the token has already been refreshed by another concurrent request
            if (currentToken != null && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            // Otherwise, perform the refresh
            val res = runBlocking {
                try {
                    apiService.value.getDelta(SyncRequest(null)) // Dummy call just to test connectivity, wait, no.
                    // The user wants me to call POST /auth/refresh
                    apiService.value.refresh(RefreshRequest(refreshToken))
                } catch (e: Exception) {
                    null
                }
            }

            if (res != null && res.isSuccessful) {
                val newTokens = res.body()
                if (newTokens != null) {
                    tokenDataStore.saveTokens(newTokens.accessToken, newTokens.refreshToken)
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                        .build()
                }
            }

            // Refresh failed
            tokenDataStore.clearTokens()
            // Here we should ideally trigger a logout event to the UI
            return null
        }
    }
}
