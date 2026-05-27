package com.runerback.remotecp.data.api

import com.runerback.remotecp.data.model.Message
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface MessageApi {

    @GET("/api/messages")
    suspend fun getMessages(): Response<Map<String, List<Message>>>

    @Multipart
    @POST("/api/messages")
    suspend fun createMessage(
        @Part("text") text: RequestBody?,
        @Part("device_type") deviceType: RequestBody,
        @Part("client_timestamp") clientTimestamp: RequestBody,
        @Part images: List<MultipartBody.Part>?,
        @Part videos: List<MultipartBody.Part>?,
        @Part files: List<MultipartBody.Part>?
    ): Response<Message>
}
