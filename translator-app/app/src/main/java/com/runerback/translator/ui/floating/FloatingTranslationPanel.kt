package com.runerback.translator.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

sealed interface TranslationState {
    data object Idle : TranslationState
    data object Loading : TranslationState
    data class Success(val text: String, val sourceText: String? = null) : TranslationState
    data class Error(val message: String) : TranslationState
}

@Composable
fun FloatingTranslationPanel(
    state: TranslationState,
    anchor: Rect,
    showSimplify: Boolean,
    onSimplify: () -> Unit,
    onChinese: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var offsetX by remember { mutableIntStateOf(0) }
    var offsetY by remember { mutableIntStateOf(0) }

    Popup(
        alignment = Alignment.TopStart,
        offset = with(density) {
            val x = anchor.left.toInt() + offsetX
            val y = anchor.bottom.toInt() + offsetY
            IntOffset(x, y)
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = with(density) { (screenWidthPx * 0.9f).toInt().toDp() })
                .background(Color.White)
                .border(2.dp, Color.Black)
                .padding(12.dp)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        offsetX += dragAmount.x.roundToInt()
                        offsetY += dragAmount.y.roundToInt()
                    }
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Translation",
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color.Black,
                )
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.padding(0.dp),
                ) {
                    Text("X", fontSize = 14.sp)
                }
            }

            when (state) {
                is TranslationState.Loading -> {
                    Text(
                        text = "Translating...",
                        fontSize = 16.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                is TranslationState.Success -> {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        state.sourceText?.let { source ->
                            Text(
                                text = source,
                                fontSize = 14.sp,
                                color = Color.DarkGray,
                            )
                        }
                        Text(
                            text = state.text,
                            fontSize = 16.sp,
                            color = Color.Black,
                        )
                    }
                }
                is TranslationState.Error -> {
                    Text(
                        text = state.message,
                        fontSize = 16.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                else -> {}
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showSimplify) {
                    OutlinedButton(
                        onClick = onSimplify,
                        enabled = state !is TranslationState.Loading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Simplify", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(
                    onClick = onChinese,
                    enabled = state !is TranslationState.Loading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Chinese", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
