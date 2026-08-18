package com.runerback.queuehelper.data.model

private const val SECTION_SUBJECT_DEFINITIONS = "subject_definitions:"
private const val SECTION_SUMMARY = "summary:"
private const val SECTION_RETENTION_ANALYSIS = "retention_analysis:"
private const val SECTION_DETAILED_DESCRIPTION = "detailed_description:"
private const val SECTION_NON_DIEGETIC_MUSIC = "non_diegetic_music:"

/**
 * Decomposed MiniMax H3 Ref2VA prompt used for editing.
 *
 * The wire format is a single string with sections joined by `\n`.
 */
data class MiniMaxH3Ref2VaPrompt(
    val subjectDefinitions: String = "",
    val summary: String = "",
    val retentionAnalysis: String = "",
    val detailedDescription: String = "",
    val nonDiegeticMusic: String = ""
) {
    fun toPromptString(): String = buildString {
        appendLine(SECTION_SUBJECT_DEFINITIONS)
        appendLine(subjectDefinitions.trim())
        appendLine(SECTION_SUMMARY)
        appendLine(summary.trim())
        appendLine(SECTION_RETENTION_ANALYSIS)
        appendLine(retentionAnalysis.trim())
        appendLine(SECTION_DETAILED_DESCRIPTION)
        appendLine(detailedDescription.trim())
        append(SECTION_NON_DIEGETIC_MUSIC)
        appendLine()
        append(nonDiegeticMusic.trim())
    }

    companion object {
        fun parse(prompt: String): MiniMaxH3Ref2VaPrompt {
            val trimmed = prompt.trim()
            if (trimmed.isEmpty()) return MiniMaxH3Ref2VaPrompt()

            val sections = linkedMapOf(
                SECTION_SUBJECT_DEFINITIONS to "",
                SECTION_SUMMARY to "",
                SECTION_RETENTION_ANALYSIS to "",
                SECTION_DETAILED_DESCRIPTION to "",
                SECTION_NON_DIEGETIC_MUSIC to ""
            )

            val orderedKeys = sections.keys.toList()
            var currentKey: String? = null
            val builder = StringBuilder()

            fun flush() {
                currentKey?.let { key ->
                    sections[key] = builder.toString().trim()
                }
                builder.clear()
            }

            trimmed.lines().forEach { rawLine ->
                val line = rawLine.trimEnd()
                val nextKey = orderedKeys.find { line.trimStart().startsWith(it) }
                if (nextKey != null) {
                    flush()
                    currentKey = nextKey
                    val remainder = line.trimStart().removePrefix(nextKey).trimStart()
                    if (remainder.isNotEmpty()) {
                        builder.appendLine(remainder)
                    }
                } else if (currentKey != null) {
                    builder.appendLine(line)
                }
            }
            flush()

            return MiniMaxH3Ref2VaPrompt(
                subjectDefinitions = sections[SECTION_SUBJECT_DEFINITIONS].orEmpty(),
                summary = sections[SECTION_SUMMARY].orEmpty(),
                retentionAnalysis = sections[SECTION_RETENTION_ANALYSIS].orEmpty(),
                detailedDescription = sections[SECTION_DETAILED_DESCRIPTION].orEmpty(),
                nonDiegeticMusic = sections[SECTION_NON_DIEGETIC_MUSIC].orEmpty()
            )
        }
    }
}
