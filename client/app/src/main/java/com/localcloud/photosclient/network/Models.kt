package com.localcloud.photosclient.network

import com.google.gson.annotations.SerializedName

data class HashCheckResponse(
    @SerializedName("exists") val exists: Boolean
)

data class RemoteMedia(
    @SerializedName("id") val id: String
)

data class MediaRemoteDTO(
    @SerializedName("id") val id: String,
    @SerializedName("originalFileName") val originalFileName: String,
    @SerializedName("fileSize") val fileSize: Long,
    @SerializedName("mediaType") val mediaType: String,
    @SerializedName("uploadDate") val uploadDate: java.util.Date,
    @SerializedName("originalCreationDate") val originalCreationDate: java.util.Date?,
    @SerializedName("deviceId") val deviceId: String?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
    @SerializedName("duration") val duration: Long?
)

data class SyncRequest(
    @SerializedName("lastSyncedAt") val lastSyncedAt: java.util.Date?
)

data class SyncResponse(
    @SerializedName("delta") val delta: List<MediaRemoteDTO>
)

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("expiresIn") val expiresIn: Long
)

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

