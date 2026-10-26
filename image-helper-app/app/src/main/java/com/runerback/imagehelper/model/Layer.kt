package com.runerback.imagehelper.model

data class Layer(
    val id: String,
    val name: String,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val elementIds: List<String> = emptyList()
)
