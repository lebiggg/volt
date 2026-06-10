package com.tonnomdeved.volt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tonnomdeved.volt.data.VoltPreferences
import com.tonnomdeved.volt.ui.navigation.VoltDestination
import com.tonnomdeved.volt.ui.screens.dashboard.DashboardScreen
import com.tonnomdeved.volt.ui.screens.hibernate.HibernateScreen
import com.tonnomdeved.volt.ui.screens.push.PushConfigScreen
import com.tonnomdeved.volt.ui.screens.settings.SettingsScreen
import com.tonnomdeved.volt.ui.theme.VoltTheme

private const val SETTINGS_ROUTE = "settings"
private const val GITHUB_URL = "https://github.com/lebiggg/volt"

/**
 * Point d'entrée UI unique. Orchestre uniquement navigation + thème.
 * Le thème lit les préférences DataStore et réagit en direct.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { VoltPreferences(context.applicationContext) }
            val themeMode by prefs.themeMode
                .collectAsStateWithLifecycle(VoltPreferences.ThemeMode.SYSTEM)
            val dynamicColor by prefs.dynamicColor.collectAsStateWithLifecycle(true)

            val dark = when (themeMode) {
                VoltPreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                VoltPreferences.ThemeMode.LIGHT  -> false
                VoltPreferences.ThemeMode.DARK   -> true
            }

            VoltTheme(darkTheme = dark, dynamicColor = dynamicColor) {
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
    val context = LocalContext.current

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
                        icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                        label = { Text(stringResource(dest.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = VoltDestination.Dashboard.route
        ) {
            composable(VoltDestination.Dashboard.route) {
                DashboardScreen(
                    contentPadding = innerPadding,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) }
                )
            }
            composable(VoltDestination.Push.route) {
                PushConfigScreen(contentPadding = innerPadding)
            }
            composable(VoltDestination.Hibernate.route) {
                HibernateScreen(contentPadding = innerPadding)
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    contentPadding = innerPadding,
                    onOpenGithub = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }
        }
    }
}
