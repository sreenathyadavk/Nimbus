package com.localcloud.photosclient.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // This should point to your backend local network IP, customizable in settings
    var BASE_URL = "http://10.0.2.2:8080/" 

    private var retrofit: Retrofit? = null

    fun getClient(context: Context): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val deviceAuthInterceptor = Interceptor { chain ->
            val prefs = context.getSharedPreferences("photos_prefs", Context.MODE_PRIVATE)
            var deviceId = prefs.getString("device_id", null)
            
            if (deviceId == null) {
                deviceId = java.util.UUID.randomUUID().toString()
                prefs.edit().putString("device_id", deviceId).apply()
            }

            val requestBuilder = chain.request().newBuilder()
            requestBuilder.addHeader("X-Device-Id", deviceId)
            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(deviceAuthInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES) // specific for large file uploads
            .writeTimeout(10, TimeUnit.MINUTES)
            .build()
    }

    fun getService(context: Context): ApiService {
        if (retrofit == null) {
            val client = getClient(context)

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }

    fun reset() {
        retrofit = null
    }
}
