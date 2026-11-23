package com.runerback.videohelper.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.runerback.videohelper.ui.editor.EditorScreen
import com.runerback.videohelper.ui.projects.ProjectsScreen

object Routes {
    const val PROJECTS = "projects"
    const val EDITOR = "editor/{projectId}"

    fun editor(projectId: String) = "editor/$projectId"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    // No content insets here: each screen's own TopAppBar handles the status bar
    // inset; applying them at both levels doubles the top gap.
    Scaffold(modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0)) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.PROJECTS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.PROJECTS) {
                ProjectsScreen(
                    onOpenProject = { projectId -> navController.navigate(Routes.editor(projectId)) },
                )
            }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            ) {
                EditorScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
