package com.runerback.translator.argostranslate

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.runerback.translator.translate.TranslationResult
import com.runerback.translator.translate.Translator
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class ArgosTranslateClient(
    baseUrl: String,
    private val sourceLanguage: String = DEFAULT_SOURCE_LANGUAGE,
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

    private val api = retrofit.create(ArgosTranslateApi::class.java)

    override suspend fun translateToEnglish(text: String): TranslationResult =
        translate(text = text, target = TARGET_ENGLISH)

    override suspend fun simplifyEnglish(text: String): TranslationResult {
        // ArgosTranslate only translates; fall back to English translation.
        return translate(text = text, target = TARGET_ENGLISH)
    }

    override suspend fun translateToChinese(text: String): TranslationResult =
        translate(text = text, target = TARGET_CHINESE)

    private suspend fun translate(text: String, target: String): TranslationResult {
        val request = ArgosTranslateRequest(
            text = text,
            source = sourceLanguage,
            target = target,
        )
        val response = api.translate(request)
        return TranslationResult(text = response.text)
    }

    companion object {
        private const val DEFAULT_SOURCE_LANGUAGE = "en"
        private const val TARGET_ENGLISH = "en"
        private const val TARGET_CHINESE = "zh"
    }
}
