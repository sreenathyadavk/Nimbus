package com.localcloud.photosclient.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.localcloud.photosclient.network.ApiService

object ApiClient {
    // This should point to your backend local network IP, customizable in settings
    var BASE_URL = "http://10.0.2.2:8080/" 

    fun getService(context: Context): ApiService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
