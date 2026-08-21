package com.runerback.translator.argostranslate

import kotlinx.serialization.Serializable

@Serializable
data class ArgosTranslateResponse(
    val text: String,
)
