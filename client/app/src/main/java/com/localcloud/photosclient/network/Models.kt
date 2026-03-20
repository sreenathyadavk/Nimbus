package com.localcloud.photosclient.network

import com.google.gson.annotations.SerializedName

data class HashCheckResponse(
    @SerializedName("exists") val exists: Boolean
)

data class RemoteMedia(
    @SerializedName("id") val id: String
)

