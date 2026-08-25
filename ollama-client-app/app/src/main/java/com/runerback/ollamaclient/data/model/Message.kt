package com.runerback.ollamaclient.data.model

import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val thinking: String = "",
)
