package com.localcloud.photosclient.di

import com.localcloud.photosclient.data.preferences.TokenDataStore
import com.localcloud.photosclient.network.AuthInterceptor
import com.localcloud.photosclient.network.TokenAuthenticator
import android.content.Context
import com.localcloud.photosclient.network.ApiService
import com.localcloud.photosclient.network.ApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTokenDataStore(@ApplicationContext context: Context): TokenDataStore {
        return TokenDataStore(context)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenDataStore: TokenDataStore): AuthInterceptor {
        return AuthInterceptor(tokenDataStore)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
        tokenDataStore: TokenDataStore,
        apiService: dagger.Lazy<ApiService>
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .authenticator(TokenAuthenticator(tokenDataStore, apiService))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(client: OkHttpClient): ApiService {
        return Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ApiServiceEntryPoint {
        fun apiService(): ApiService
    }
}
