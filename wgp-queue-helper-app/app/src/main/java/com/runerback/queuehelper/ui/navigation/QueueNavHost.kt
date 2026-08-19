package com.runerback.queuehelper.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.runerback.queuehelper.ui.edit.EditPresetScreen
import com.runerback.queuehelper.ui.pack.PackScreen
import com.runerback.queuehelper.ui.pack.TaskEditor
import com.runerback.queuehelper.ui.presets.PresetListScreen
import kotlinx.serialization.Serializable

@Serializable
data object PresetListRoute

@Serializable
data class EditPresetRoute(val presetId: Int)

@Serializable
data class PackRoute(val presetId: Int)

@Serializable
data class TaskEditorRoute(val taskId: Int)

@Composable
fun QueueNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = PresetListRoute,
        modifier = modifier
    ) {
        composable<PresetListRoute> {
            PresetListScreen(
                onEditPreset = { presetId ->
                    navController.navigate(EditPresetRoute(presetId))
                },
                onPackPreset = { presetId ->
                    navController.navigate(PackRoute(presetId))
                }
            )
        }

        composable<EditPresetRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EditPresetRoute>()
            EditPresetScreen(
                presetId = route.presetId,
                onBack = { navController.popBackStack() }
            )
        }

        composable<PackRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PackRoute>()
            PackScreen(
                presetId = route.presetId,
                onEditTask = { taskId ->
                    navController.navigate(TaskEditorRoute(taskId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<TaskEditorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<TaskEditorRoute>()
            TaskEditor(
                taskId = route.taskId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
