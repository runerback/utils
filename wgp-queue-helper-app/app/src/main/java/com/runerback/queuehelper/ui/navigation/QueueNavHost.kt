package com.runerback.queuehelper.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.runerback.queuehelper.ui.edit.EditTaskScreen
import com.runerback.queuehelper.ui.pack.PackTaskScreen
import com.runerback.queuehelper.ui.tasks.TaskListScreen
import kotlinx.serialization.Serializable

@Serializable
data object TaskListRoute

@Serializable
data class EditTaskRoute(val taskId: Int)

@Serializable
data class PackTaskRoute(val taskId: Int)

@Composable
fun QueueNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TaskListRoute,
        modifier = modifier
    ) {
        composable<TaskListRoute> {
            TaskListScreen(
                onEditTask = { taskId ->
                    navController.navigate(EditTaskRoute(taskId))
                },
                onPackTask = { taskId ->
                    navController.navigate(PackTaskRoute(taskId))
                }
            )
        }

        composable<EditTaskRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EditTaskRoute>()
            EditTaskScreen(
                taskId = route.taskId,
                onBack = { navController.popBackStack() }
            )
        }

        composable<PackTaskRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PackTaskRoute>()
            PackTaskScreen(
                taskId = route.taskId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
