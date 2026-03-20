package com.localcloud.photosclient.di

import android.content.Context
import com.localcloud.photosclient.network.ApiClient
import com.localcloud.photosclient.network.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApiService(@ApplicationContext context: Context): ApiService {
        return ApiClient.getService(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): okhttp3.OkHttpClient {
        return ApiClient.getClient(context) // We will add getClient to ApiClient
    }
}
