package com.runerback.comfyuiapi.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FilePickerSection(
    workflowName: String,
    schemaName: String,
    onWorkflowPicked: (Uri) -> Unit,
    onSchemaPicked: (Uri) -> Unit,
    onSaveSchemaDefaults: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val workflowLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onWorkflowPicked) }
    )
    val schemaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onSchemaPicked) }
    )
    val saveDefaultsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let(onSaveSchemaDefaults) }
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { workflowLauncher.launch(arrayOf("application/json")) }
            ) {
                Text("Pick workflow")
            }
            Text(
                text = workflowName,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { schemaLauncher.launch(arrayOf("application/json")) }
            ) {
                Text("Pick schema")
            }
            Text(
                text = schemaName,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (schemaName.isNotBlank()) {
                IconButton(
                    onClick = {
                        val defaultName = schemaName.takeIf { it.isNotBlank() } ?: "defaults.schema.json"
                        saveDefaultsLauncher.launch(defaultName)
                    },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save schema defaults",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
