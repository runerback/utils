package com.runerback.ollamaclient.data.model

data class Message(
    val role: String,
    val content: String,
    val thinking: String = "",
)
