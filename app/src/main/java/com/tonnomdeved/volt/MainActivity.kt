package com.tonnomdeved.volt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tonnomdeved.volt.ui.navigation.VoltDestination
import com.tonnomdeved.volt.ui.screens.dashboard.DashboardScreen
import com.tonnomdeved.volt.ui.screens.hibernate.HibernateScreen
import com.tonnomdeved.volt.ui.screens.push.PushConfigScreen
import com.tonnomdeved.volt.ui.theme.VoltTheme

/**
 * Point d'entrée UI unique.
 *
 * Le `MainActivity` est volontairement minimaliste : il n'orchestre que la
 * navigation et le theme. Toute logique réseau / service est confinée dans
 * les ViewModels ou le `BatteryCommandService`.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoltTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    VoltApp()
                }
            }
        }
    }
}

@Composable
private fun VoltApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                VoltDestination.all.forEach { dest ->
                    val selected = backStackEntry?.destination
                        ?.hierarchy
                        ?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != dest.route) {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        VoltNavHost(navController = navController, innerPadding = innerPadding)
    }
}

@Composable
private fun VoltNavHost(
    navController: androidx.navigation.NavHostController,
    innerPadding: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = VoltDestination.Dashboard.route
    ) {
        composable(VoltDestination.Dashboard.route) {
            DashboardScreen(contentPadding = innerPadding)
        }
        composable(VoltDestination.Push.route) {
            PushConfigScreen(contentPadding = innerPadding)
        }
        composable(VoltDestination.Hibernate.route) {
            HibernateScreen(contentPadding = innerPadding)
        }
    }
}
