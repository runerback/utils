package com.runerback.translator.ollama

import com.runerback.translator.data.OllamaRequest
import com.runerback.translator.data.OllamaResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaApi {

    @POST("/api/chat")
    suspend fun chat(@Body request: OllamaRequest): OllamaResponse
}
