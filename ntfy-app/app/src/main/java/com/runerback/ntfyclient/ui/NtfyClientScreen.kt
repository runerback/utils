package com.runerback.ntfyclient.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.runerback.ntfyclient.R
import com.runerback.ntfyclient.ui.home.HomeScreen
import com.runerback.ntfyclient.ui.logs.LogsScreen
import com.runerback.ntfyclient.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object LogsRoute

@Serializable
object SettingsRoute

private data class TopLevelRoute<T : Any>(
    val name: String,
    val route: T,
    val icon: @Composable () -> Unit
)

@Composable
fun NtfyClientScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val topLevelRoutes = listOf(
        TopLevelRoute(
            name = stringResource(R.string.home),
            route = HomeRoute,
            icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home)) }
        ),
        TopLevelRoute(
            name = stringResource(R.string.logs),
            route = LogsRoute,
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.logs)) }
        ),
        TopLevelRoute(
            name = stringResource(R.string.settings),
            route = SettingsRoute,
            icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) }
        )
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelRoutes.forEach { item ->
                    val selected = currentDestination?.hasRoute(item.route::class) == true
                    NavigationBarItem(
                        icon = item.icon,
                        label = { Text(item.name) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<HomeRoute> { HomeScreen() }
            composable<LogsRoute> { LogsScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}
