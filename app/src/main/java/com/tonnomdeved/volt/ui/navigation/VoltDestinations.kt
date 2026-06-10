package com.tonnomdeved.volt.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sealed class des routes — single source of truth pour la NavBar et le NavHost.
 *
 * 3 onglets : Dashboard | Push | Hibernate.
 * (L'ancien onglet "Apps" — Deep Sleep historique basé DataStore — a été
 * fusionné dans Hibernate, qui couvre le même besoin avec Room + scoring.)
 */
sealed class VoltDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Dashboard  : VoltDestination("dashboard", "Tableau",   Icons.Outlined.Dashboard)
    data object Push       : VoltDestination("push",      "Push",      Icons.Outlined.CloudSync)
    data object Hibernate  : VoltDestination("hibernate", "Hibernate", Icons.Outlined.Snooze)

    companion object {
        val all: List<VoltDestination> = listOf(Dashboard, Push, Hibernate)
    }
}
