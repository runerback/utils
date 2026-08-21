package com.runerback.translator.argostranslate

import kotlinx.serialization.Serializable

@Serializable
data class ArgosTranslateRequest(
    val text: String,
    val source: String,
    val target: String,
)
