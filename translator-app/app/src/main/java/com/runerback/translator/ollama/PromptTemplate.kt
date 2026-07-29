package com.runerback.translator.ollama

object PromptTemplate {

    fun translateToEnglish(text: String): String = buildString {
        appendLine("Step 1: Identify the source language. Output ONLY the language name.")
        appendLine("Step 2: Translate the following text into natural, fluent English suitable for a novel. Preserve the literary tone and plot details.")
        appendLine()
        appendLine("Text:")
        appendLine("\"$text\"")
    }

    fun simplifyEnglish(text: String): String = buildString {
        appendLine("Rewrite the following advanced English text into beginner-level English (CEFR A2 level). Use only common, everyday vocabulary. Keep sentences short and simple. Preserve the exact meaning and plot details. Do not add explanations, just output the simplified text.")
        appendLine()
        appendLine("Text:")
        appendLine("\"$text\"")
    }

    fun translateToChinese(text: String): String = buildString {
        appendLine("Translate the following English text into Simplified Chinese. Use natural, modern Chinese prose suitable for a novel.")
        appendLine()
        appendLine("Text:")
        appendLine("\"$text\"")
    }
}
