package com.runerback.queuehelper.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.runerback.queuehelper.ui.components.LogViewDialog
import com.runerback.queuehelper.ui.icons.TablerLogs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.queuehelper.QueueHelperApplication
import com.runerback.queuehelper.data.model.Task
import com.runerback.queuehelper.ui.icons.FluentuiSystemIconsFolderZip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onEditTask: (Int) -> Unit,
    onPackTask: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QueueHelperApplication
    val viewModel: TaskListViewModel = viewModel(
        factory = TaskListViewModel.Factory(app.taskRepository, app.templateLoader)
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TaskListViewModel.TaskListEvent.NavigateToEdit -> onEditTask(event.taskId)
            }
        }
    }

    var showLogView by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue Helper") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.openCreateDialog() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create New"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showLogView = true }) {
                        Icon(
                            imageVector = TablerLogs,
                            contentDescription = "Logs"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (viewModel.tasks.isEmpty()) {
                Text(
                    text = "No tasks yet. Tap + to create one.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(viewModel.tasks, key = { it.id }) { task ->
                        TaskListItem(
                            task = task,
                            onEdit = { onEditTask(task.id) },
                            onPack = { onPackTask(task.id) },
                            onDelete = { viewModel.deleteTask(task) }
                        )
                    }
                }
            }

            if (viewModel.showCreateDialog) {
                CreateTaskDialog(
                    templates = app.templateLoader.load().keys.toList()
                        .map { modelType ->
                            modelType to app.templateLoader.defaultName(modelType)
                        },
                    onDismiss = { viewModel.closeCreateDialog() },
                    onCreate = { name, modelType ->
                        viewModel.createTask(name, modelType)
                    }
                )
            }

            if (showLogView) {
                LogViewDialog(onDismiss = { showLogView = false })
            }
        }
    }
}

@Composable
private fun TaskListItem(
    task: Task,
    onEdit: () -> Unit,
    onPack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = task.modelType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit task"
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onPack) {
                Icon(
                    imageVector = FluentuiSystemIconsFolderZip,
                    contentDescription = "Pack task"
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete task"
                )
            }
        }
    }
}
