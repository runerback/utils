package com.runerback.translator.reader.epub

data class EpubBook(
    val title: String,
    val author: String?,
    val chapters: List<EpubChapter>,
)
