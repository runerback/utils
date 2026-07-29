package com.runerback.translator.reader.epub

data class EpubChapter(
    val id: String,
    val href: String,
    val title: String?,
    val body: String,
)
