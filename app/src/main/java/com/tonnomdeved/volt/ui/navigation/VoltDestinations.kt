package com.tonnomdeved.volt.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.ui.graphics.vector.ImageVector
import com.tonnomdeved.volt.R

/**
 * Sealed class des routes — single source of truth pour la NavBar et le NavHost.
 *
 * 4 onglets : Dashboard | Push | Hibernate | Forensics.
 * Les labels sont des resource IDs (i18n en/fr).
 */
sealed class VoltDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    data object Dashboard  : VoltDestination("dashboard", R.string.tab_dashboard, Icons.Outlined.Dashboard)
    data object Push       : VoltDestination("push",      R.string.tab_push,      Icons.Outlined.CloudSync)
    data object Hibernate  : VoltDestination("hibernate", R.string.tab_hibernate, Icons.Outlined.Snooze)
    data object Forensics  : VoltDestination("forensics", R.string.tab_forensics, Icons.Outlined.MonitorHeart)

    companion object {
        val all: List<VoltDestination> = listOf(Dashboard, Push, Hibernate, Forensics)
    }
}
