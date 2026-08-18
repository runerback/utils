package com.runerback.queuehelper.ui.pack

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.runerback.queuehelper.data.model.SubjectDefinition

@Composable
fun SubjectEditorDialog(
    subject: SubjectDefinition?, // null means create new
    imageUris: List<Uri>,
    onDismiss: () -> Unit,
    onConfirm: (description: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var description by remember(subject) { mutableStateOf(subject?.description ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (subject == null) "Add Subject" else "Edit Subject",
                    style = MaterialTheme.typography.titleMedium
                )

                TokenTextField(
                    value = description,
                    onValueChange = { description = it },
                    subjects = emptyList(),
                    imageUris = imageUris,
                    label = { Text("Description") },
                    availableSubjects = false,
                    availablePictures = true,
                    minLines = 3,
                    maxLines = 6
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            onConfirm(description)
                            onDismiss()
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
