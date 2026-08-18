package com.runerback.queuehelper.data.model

private val SUBJECT_LINE_REGEX = Regex(
    "^\\s*<Subject\\s+(\\d+)>\\s*(.*)$",
    RegexOption.IGNORE_CASE
)

private val AUDIO_LINE_REGEX = Regex(
    "^\\s*<Audio\\s+1>\\s*:\\s*(.*)$",
    RegexOption.IGNORE_CASE
)

private val PICTURE_TOKEN_REGEX = Regex(
    "<Picture\\s+(\\d+)>",
    RegexOption.IGNORE_CASE
)

private const val DEFAULT_AUDIO_DEFINITION =
    "<Audio 1>: fully_copy - the entire source audio is preserved as the final track."

/**
 * One subject entry inside the `subject_definitions` prompt section.
 *
 * The [description] is the raw text after `<Subject N>` and may contain literal
 * `<Picture N>` tokens.
 */
data class SubjectDefinition(
    val id: Int,
    val number: Int,
    val description: String
) {
    fun format(): String = "<Subject $number> ${description.trim()}".trimEnd()

    companion object {
        fun defaultAudioDefinition(): String = DEFAULT_AUDIO_DEFINITION
    }
}

sealed class DescriptionSegment {
    data class Text(val text: String) : DescriptionSegment()
    data class Picture(val number: Int) : DescriptionSegment()
}

/**
 * Splits a subject description into plain text pieces and picture tokens so they
 * can be rendered inline.
 */
fun parseDescriptionSegments(description: String): List<DescriptionSegment> {
    val segments = mutableListOf<DescriptionSegment>()
    var cursor = 0

    PICTURE_TOKEN_REGEX.findAll(description).forEach { match ->
        if (match.range.first > cursor) {
            segments.add(DescriptionSegment.Text(description.substring(cursor, match.range.first)))
        }
        val number = match.groupValues[1].toIntOrNull() ?: 1
        segments.add(DescriptionSegment.Picture(number))
        cursor = match.range.last + 1
    }

    if (cursor < description.length) {
        segments.add(DescriptionSegment.Text(description.substring(cursor)))
    }

    return segments
}

fun replacePictureToken(description: String, oldNumber: Int, newNumber: Int): String {
    val pattern = Regex("<Picture\\s+${oldNumber}>", RegexOption.IGNORE_CASE)
    return description.replaceFirst(pattern, "<Picture $newNumber>")
}

/**
 * Returns the first picture number referenced in a subject description, or null
 * if the description does not reference any picture.
 */
fun firstPictureNumber(description: String): Int? {
    return PICTURE_TOKEN_REGEX.find(description)
        ?.groupValues?.get(1)
        ?.toIntOrNull()
}

fun removeDescriptionSegment(description: String, segmentIndex: Int): String {
    val segments = parseDescriptionSegments(description)
    if (segmentIndex !in segments.indices) return description
    val newSegments = segments.toMutableList().apply { removeAt(segmentIndex) }
    return buildString {
        newSegments.forEach { segment ->
            when (segment) {
                is DescriptionSegment.Text -> append(segment.text)
                is DescriptionSegment.Picture -> append("<Picture ${segment.number}>")
            }
        }
    }.trim()
}

sealed class EditableSegment {
    data class Text(val text: String) : EditableSegment()
    data class Subject(val number: Int) : EditableSegment()
    data class Picture(val number: Int) : EditableSegment()
    data class Audio(val number: Int) : EditableSegment()
}

private val TOKEN_REGEX = Regex(
    "(<Subject\\s+(\\d+)>)|(<Picture\\s+(\\d+)>)|(<Audio\\s+(\\d+)>)",
    RegexOption.IGNORE_CASE
)

/**
 * Splits an editable prompt-section, subject-description, or audio-definition
 * string into plain text pieces and token references. This is the UI-side model;
 * the wire format still uses literal `<Subject N>` / `<Picture N>` / `<Audio N>` strings.
 */
fun parseEditableSegments(text: String): List<EditableSegment> {
    val segments = mutableListOf<EditableSegment>()
    var cursor = 0

    TOKEN_REGEX.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            segments.add(EditableSegment.Text(text.substring(cursor, match.range.first)))
        }
        val subjectNumber = match.groupValues[2].toIntOrNull()
        val pictureNumber = match.groupValues[4].toIntOrNull()
        val audioNumber = match.groupValues[6].toIntOrNull()
        when {
            subjectNumber != null -> segments.add(EditableSegment.Subject(subjectNumber))
            pictureNumber != null -> segments.add(EditableSegment.Picture(pictureNumber))
            audioNumber != null -> segments.add(EditableSegment.Audio(audioNumber))
        }
        cursor = match.range.last + 1
    }

    if (cursor < text.length) {
        segments.add(EditableSegment.Text(text.substring(cursor)))
    }

    return segments
}

fun formatEditableSegments(segments: List<EditableSegment>): String = buildString {
    segments.forEach { segment ->
        when (segment) {
            is EditableSegment.Text -> append(segment.text)
            is EditableSegment.Subject -> append("<Subject ${segment.number}>")
            is EditableSegment.Picture -> append("<Picture ${segment.number}>")
            is EditableSegment.Audio -> append("<Audio ${segment.number}>")
        }
    }
}

/**
 * Parses a `subject_definitions` text block.
 *
 * @return parsed subjects, the audio definition line if present, and any other unrecognized lines.
 */
fun parseSubjectDefinitions(text: String): Triple<List<SubjectDefinition>, String?, String> {
    val subjects = mutableListOf<SubjectDefinition>()
    var audioLine: String? = null
    val otherLines = StringBuilder()
    var nextId = 1

    text.trim().lines().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEach

        val subjectMatch = SUBJECT_LINE_REGEX.matchEntire(line)
        if (subjectMatch != null) {
            val number = subjectMatch.groupValues[1].toIntOrNull() ?: nextId
            val description = subjectMatch.groupValues[2].trim()
            subjects.add(
                SubjectDefinition(
                    id = nextId++,
                    number = number,
                    description = description
                )
            )
            return@forEach
        }

        val audioMatch = AUDIO_LINE_REGEX.matchEntire(line)
        if (audioMatch != null) {
            audioLine = line
            return@forEach
        }

        otherLines.appendLine(line)
    }

    return Triple(subjects, audioLine, otherLines.toString().trim())
}

/**
 * Builds the `subject_definitions` text from managed subjects.
 *
 * The audio definition line is always emitted last when present.
 */
fun formatSubjectDefinitions(
    subjects: List<SubjectDefinition>,
    audioLine: String?,
    otherLines: String = ""
): String = buildString {
    subjects.forEach { appendLine(it.format()) }
    if (otherLines.isNotEmpty()) {
        appendLine(otherLines)
    }
    audioLine?.let { appendLine(it) }
}.trimEnd()

/**
 * Stable defaults for subjects and the audio definition, stored per task so the
 * pack screen can auto-fill them.
 */
@kotlinx.serialization.Serializable
data class SubjectDefaults(
    val subjects: List<SubjectDefault> = emptyList(),
    val audio: String = SubjectDefinition.defaultAudioDefinition()
)

@kotlinx.serialization.Serializable
data class SubjectDefault(
    val number: Int,
    val description: String
)
