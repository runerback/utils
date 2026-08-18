package com.runerback.queuehelper.data.model

private val TOKEN_REGEX = Regex(
    "(<Subject\\s+(\\d+)>)|(<Picture\\s+(\\d+)>)|(<Audio\\s+(\\d+)>)",
    RegexOption.IGNORE_CASE
)

sealed class Token {
    data class PlainText(val text: String) : Token()
    data class Subject(val number: Int) : Token()
    data class Picture(val number: Int) : Token()
    data class Audio(val number: Int) : Token()
}

fun String.parseTokens(): List<Token> {
    val segments = mutableListOf<Token>()
    var cursor = 0

    TOKEN_REGEX.findAll(this).forEach { match ->
        if (match.range.first > cursor) {
            segments.add(Token.PlainText(substring(cursor, match.range.first)))
        }
        val subjectNumber = match.groupValues[2].toIntOrNull()
        val pictureNumber = match.groupValues[4].toIntOrNull()
        val audioNumber = match.groupValues[6].toIntOrNull()
        when {
            subjectNumber != null -> segments.add(Token.Subject(subjectNumber))
            pictureNumber != null -> segments.add(Token.Picture(pictureNumber))
            audioNumber != null -> segments.add(Token.Audio(audioNumber))
        }
        cursor = match.range.last + 1
    }

    if (cursor < length) {
        segments.add(Token.PlainText(substring(cursor)))
    }

    return segments
}

fun List<Token>.export(): String {
    val builder = StringBuilder()
    for (item in this) {
        when (item) {
            is Token.PlainText -> builder.append(item.text)
            is Token.Subject -> builder.append("<Subject ${item.number}>")
            is Token.Picture -> builder.append("<Picture ${item.number}>")
            is Token.Audio -> builder.append("<Audio ${item.number}>")
        }
    }
    return builder.toString()
}

fun List<Token>.mergeAdjacentPlainText(): List<Token> {
    if (isEmpty()) return this
    val result = mutableListOf<Token>()
    var pendingText: String? = null
    forEach { token ->
        when (token) {
            is Token.PlainText -> {
                pendingText = (pendingText ?: "") + token.text
            }
            else -> {
                pendingText?.let { result.add(Token.PlainText(it)) }
                pendingText = null
                result.add(token)
            }
        }
    }
    pendingText?.let { result.add(Token.PlainText(it)) }
    return result
}

fun MutableList<Token>.splitAndInsert(index: Int, cursor: Int, token: Token) {
    val current = get(index)
    require(current is Token.PlainText)
    val text = current.text
    val before = text.substring(0, cursor.coerceIn(0, text.length))
    val after = text.substring(cursor.coerceIn(0, text.length))
    set(index, Token.PlainText(before))
    add(index + 1, token)
    add(index + 2, Token.PlainText(after))
}

fun List<Token>.removeTokenAt(index: Int): List<Token> {
    if (index !in indices) return this
    return toMutableList().apply { removeAt(index) }.mergeAdjacentPlainText()
}
