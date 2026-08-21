package com.runerback.translator.ollama

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.runerback.translator.data.OllamaMessage
import com.runerback.translator.data.OllamaOptions
import com.runerback.translator.data.OllamaRequest
import com.runerback.translator.data.OllamaResponse
import com.runerback.translator.translate.TranslationResult
import com.runerback.translator.translate.Translator
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class OllamaClient(
    baseUrl: String,
    model: String = DEFAULT_MODEL,
    temperature: Double = DEFAULT_TEMPERATURE,
) : Translator {

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(OllamaApi::class.java)

    private val model = model
    private val temperature = temperature

    override suspend fun translateToEnglish(text: String): TranslationResult = chat(
        content = PromptTemplate.translateToEnglish(text),
    ).toResult()

    override suspend fun simplifyEnglish(text: String): TranslationResult = chat(
        content = PromptTemplate.simplifyEnglish(text),
    ).toResult()

    override suspend fun translateToChinese(text: String): TranslationResult = chat(
        content = PromptTemplate.translateToChinese(text),
    ).toResult()

    private fun OllamaResponse.toResult(): TranslationResult =
        TranslationResult(text = message.content)

    private suspend fun chat(content: String): OllamaResponse {
        val request = OllamaRequest(
            model = model,
            think = false,
            stream = false,
            messages = listOf(OllamaMessage(role = "user", content = content)),
            options = OllamaOptions(temperature = temperature),
        )
        return api.chat(request)
    }

    companion object {
        private const val DEFAULT_MODEL = "qwen3:14b"
        private const val DEFAULT_TEMPERATURE = 0.2
    }
}
