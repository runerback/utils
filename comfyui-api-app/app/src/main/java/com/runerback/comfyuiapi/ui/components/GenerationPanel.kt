package com.runerback.comfyuiapi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runerback.comfyuiapi.data.model.GenerationStatus

@Composable
fun GenerationPanel(
    status: GenerationStatus,
    onGenerateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = onGenerateClick,
            enabled = status !is GenerationStatus.Connecting && status !is GenerationStatus.Running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when (status) {
                    is GenerationStatus.Connecting -> "Connecting…"
                    is GenerationStatus.Running -> "Running…"
                    else -> "Generate"
                }
            )
        }

        when (status) {
            is GenerationStatus.Connecting -> {
                Text("Connecting to server…", modifier = Modifier.padding(top = 8.dp))
            }
            is GenerationStatus.Running -> {
                val progress = status.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.first.toFloat() / progress.second.coerceAtLeast(1) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Text(
                        text = "${progress.first} / ${progress.second}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (status.currentNode != null) {
                    Text(
                        text = "Executing node: ${status.currentNode}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            is GenerationStatus.Completed -> {
                Text("Completed", modifier = Modifier.padding(top = 8.dp))
            }
            is GenerationStatus.Error -> {
                Text(
                    text = "Error: ${status.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            else -> Unit
        }
    }
}
