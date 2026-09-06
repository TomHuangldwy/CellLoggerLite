package com.celllogger.lite.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.celllogger.lite.data.model.ExperimentId
import com.celllogger.lite.ui.screens.ApiLimitsScreen
import com.celllogger.lite.ui.screens.CurrentCellScreen
import com.celllogger.lite.ui.screens.DeviceCheckScreen
import com.celllogger.lite.ui.screens.ExperimentsScreen
import com.celllogger.lite.ui.screens.HistoryScreen
import com.celllogger.lite.ui.screens.MoreScreen
import com.celllogger.lite.ui.screens.StatsScreen

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: String
)

private val topDestinations = listOf(
    TopDestination("collect", "采集", "●"),
    TopDestination("history", "历史", "≡"),
    TopDestination("stats", "统计", "∑"),
    TopDestination("experiments", "实验", "✱"),
    TopDestination("more", "更多", "☰")
)

@Composable
fun CellLoggerApp(viewModel: CellLoggerViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val selectedParent = when (currentRoute) {
        "device", "limits" -> "more"
        else -> currentRoute
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                topDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedParent == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Text(destination.icon)
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "collect",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("collect") {
                CurrentCellScreen(
                    viewModel = viewModel,
                    openDeviceCheck = { navController.navigate("device") }
                )
            }
            composable("history") {
                HistoryScreen(viewModel)
            }
            composable("stats") {
                StatsScreen(viewModel)
            }
            composable("experiments") {
                ExperimentsScreen(
                    viewModel = viewModel,
                    onExperimentSelected = { experiment: ExperimentId ->
                        viewModel.setExperiment(experiment)
                        navController.navigate("collect") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("more") {
                MoreScreen(
                    openDeviceCheck = { navController.navigate("device") },
                    openApiLimits = { navController.navigate("limits") }
                )
            }
            composable("device") {
                DeviceCheckScreen(onBack = { navController.popBackStack() })
            }
            composable("limits") {
                ApiLimitsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
