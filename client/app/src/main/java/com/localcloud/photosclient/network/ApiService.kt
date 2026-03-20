package com.localcloud.photosclient.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("api/media/hash/{hash}")
    suspend fun checkHash(@Path("hash") hash: String): Response<HashCheckResponse>

    @Multipart
    @POST("api/media/upload")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part("hash") hash: RequestBody,
        @Part("originalCreationDate") date: RequestBody
    ): Response<RemoteMedia>

    @retrofit2.http.DELETE("api/media/{id}")
    suspend fun deleteMedia(@Path("id") id: String): Response<Void>

    @POST("api/sync/delta")
    suspend fun getDelta(@Body request: SyncRequest): Response<SyncResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Void>
}
