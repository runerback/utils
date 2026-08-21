package com.runerback.translator.argostranslate

import retrofit2.http.Body
import retrofit2.http.POST

interface ArgosTranslateApi {

    @POST("/translate")
    suspend fun translate(@Body request: ArgosTranslateRequest): ArgosTranslateResponse
}
