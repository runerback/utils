package com.runerback.translator.translate

interface Translator {
    suspend fun translateToEnglish(text: String): TranslationResult
    suspend fun simplifyEnglish(text: String): TranslationResult
    suspend fun translateToChinese(text: String): TranslationResult
}
