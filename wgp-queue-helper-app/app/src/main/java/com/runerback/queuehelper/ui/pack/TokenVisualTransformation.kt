package com.runerback.queuehelper.ui.pack

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.runerback.queuehelper.data.model.Token
import com.runerback.queuehelper.data.model.mergeAdjacentPlainText
import com.runerback.queuehelper.data.model.parseTokens

internal data class VisualTokenInfo(
    val token: Token,
    val index: Int,
    val visualRange: IntRange
)

internal class TokenVisualTransformation(
    private val subjectText: Color,
    private val subjectBackground: Color,
    private val pictureText: Color,
    private val pictureBackground: Color,
    private val audioText: Color,
    private val audioBackground: Color
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val result = transformTokens(
            tokens = text.text.parseTokens().mergeAdjacentPlainText(),
            subjectText = subjectText,
            subjectBackground = subjectBackground,
            pictureText = pictureText,
            pictureBackground = pictureBackground,
            audioText = audioText,
            audioBackground = audioBackground
        )
        return TransformedText(result.visualText, result.offsetMapping)
    }

    companion object {
        fun computeVisualRanges(text: String): List<VisualTokenInfo> {
            return transformTokens(
                tokens = text.parseTokens().mergeAdjacentPlainText(),
                subjectText = Color.Unspecified,
                subjectBackground = Color.Unspecified,
                pictureText = Color.Unspecified,
                pictureBackground = Color.Unspecified,
                audioText = Color.Unspecified,
                audioBackground = Color.Unspecified
            ).visualTokens
        }
    }
}

private data class TransformResult(
    val visualText: AnnotatedString,
    val visualTokens: List<VisualTokenInfo>,
    val offsetMapping: OffsetMapping
)

private fun transformTokens(
    tokens: List<Token>,
    subjectText: Color,
    subjectBackground: Color,
    pictureText: Color,
    pictureBackground: Color,
    audioText: Color,
    audioBackground: Color
): TransformResult {
    val visualStringBuilder = StringBuilder()
    val annotatedBuilder = AnnotatedString.Builder()
    val visualTokens = mutableListOf<VisualTokenInfo>()

    tokens.forEachIndexed { index, token ->
        when (token) {
            is Token.PlainText -> {
                annotatedBuilder.append(token.text)
                visualStringBuilder.append(token.text)
            }
            is Token.Subject -> {
                val label = token.label()
                val visualStart = visualStringBuilder.length
                val visualEnd = visualStart + label.length
                visualTokens.add(VisualTokenInfo(token, index, visualStart until visualEnd))
                val pushed = annotatedBuilder.pushStyleIfSpecified(SpanStyle(color = subjectText, background = subjectBackground))
                annotatedBuilder.append(label)
                annotatedBuilder.popIfSpecified(pushed)
                visualStringBuilder.append(label)
            }
            is Token.Picture -> {
                val label = token.label()
                val visualStart = visualStringBuilder.length
                val visualEnd = visualStart + label.length
                visualTokens.add(VisualTokenInfo(token, index, visualStart until visualEnd))
                val pushed = annotatedBuilder.pushStyleIfSpecified(SpanStyle(color = pictureText, background = pictureBackground))
                annotatedBuilder.append(label)
                annotatedBuilder.popIfSpecified(pushed)
                visualStringBuilder.append(label)
            }
            is Token.Audio -> {
                val label = token.label()
                val visualStart = visualStringBuilder.length
                val visualEnd = visualStart + label.length
                visualTokens.add(VisualTokenInfo(token, index, visualStart until visualEnd))
                val pushed = annotatedBuilder.pushStyleIfSpecified(SpanStyle(color = audioText, background = audioBackground))
                annotatedBuilder.append(label)
                annotatedBuilder.popIfSpecified(pushed)
                visualStringBuilder.append(label)
            }
        }
    }

    val originalLength = tokens.sumOf { it.textLength() }
    val visualLength = visualStringBuilder.length

    val originalToTransformed = IntArray(originalLength + 1)
    val transformedToOriginal = IntArray(visualLength + 1)

    var originalOffset = 0
    var visualOffset = 0

    tokens.forEach { token ->
        when (token) {
            is Token.PlainText -> {
                val length = token.text.length
                for (i in 0..length) {
                    originalToTransformed[originalOffset + i] = visualOffset + i
                }
                for (i in 0..length) {
                    transformedToOriginal[visualOffset + i] = originalOffset + i
                }
                originalOffset += length
                visualOffset += length
            }
            else -> {
                val label = token.label()
                val tokenLength = token.textLength()
                val labelLength = label.length

                for (i in 0 until tokenLength) {
                    originalToTransformed[originalOffset + i] = visualOffset
                }
                originalToTransformed[originalOffset + tokenLength] = visualOffset + labelLength

                for (i in 0 until labelLength) {
                    transformedToOriginal[visualOffset + i] = originalOffset
                }
                transformedToOriginal[visualOffset + labelLength] = originalOffset + tokenLength

                originalOffset += tokenLength
                visualOffset += labelLength
            }
        }
    }

    val offsetMapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            return originalToTransformed.getOrElse(offset.coerceIn(0, originalLength)) { visualLength }
        }

        override fun transformedToOriginal(offset: Int): Int {
            return transformedToOriginal.getOrElse(offset.coerceIn(0, visualLength)) { originalLength }
        }
    }

    return TransformResult(annotatedBuilder.toAnnotatedString(), visualTokens, offsetMapping)
}

private fun Token.label(): String = when (this) {
    is Token.PlainText -> error("PlainText has no label")
    is Token.Subject -> "S${number}"
    is Token.Picture -> "P${number}"
    is Token.Audio -> "A${number}"
}

private fun Token.textLength(): Int = when (this) {
    is Token.PlainText -> text.length
    is Token.Subject -> "<Subject $number>".length
    is Token.Picture -> "<Picture $number>".length
    is Token.Audio -> "<Audio $number>".length
}

private fun AnnotatedString.Builder.pushStyleIfSpecified(style: SpanStyle): Boolean {
    return if (style.color != Color.Unspecified || style.background != Color.Unspecified) {
        pushStyle(style)
        true
    } else {
        false
    }
}

private fun AnnotatedString.Builder.popIfSpecified(pushed: Boolean) {
    if (pushed) {
        pop()
    }
}
