package com.runerback.translator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PdfThumbnailDialog(
    pageCount: Int,
    initialPage: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var input by remember(initialPage) { mutableStateOf(initialPage.toString()) }
    val page = input.toIntOrNull()
    val isValid = page != null && page in 1..pageCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF thumbnail page", color = Color.Black) },
        containerColor = Color.White,
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Total pages: $pageCount",
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { it.isDigit() }
                    },
                    label = { Text("Page (1-$pageCount)", color = Color.Black) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = input.isNotBlank() && !isValid,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    page?.let { onConfirm(it) }
                },
                enabled = isValid,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContentColor = Color.Gray,
                ),
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Text("Cancel")
            }
        },
    )
}
