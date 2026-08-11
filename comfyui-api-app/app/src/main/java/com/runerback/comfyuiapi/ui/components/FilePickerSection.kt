package com.runerback.comfyuiapi.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FilePickerSection(
    workflowName: String,
    schemaName: String,
    onWorkflowPicked: (android.net.Uri) -> Unit,
    onSchemaPicked: (android.net.Uri) -> Unit,
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

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Workflow files",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { workflowLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Pick workflow")
            }
            Button(
                onClick = { schemaLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text("Pick schema")
            }
        }
        if (workflowName.isNotBlank()) {
            Text(
                text = "Workflow: $workflowName",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (schemaName.isNotBlank()) {
            Text(
                text = "Schema: $schemaName",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
