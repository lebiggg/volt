package com.tonnomdeved.volt.ui.screens.dashboard

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tonnomdeved.volt.R
import com.tonnomdeved.volt.data.PermissionChecker
import com.tonnomdeved.volt.data.hibernation.shizuku.ShizukuGateway
import com.tonnomdeved.volt.ui.theme.SignalError
import com.tonnomdeved.volt.ui.theme.SignalOk
import com.tonnomdeved.volt.ui.theme.SignalWarning

@Composable
fun DashboardScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    // Refresh des permissions à chaque retour de l'utilisateur (depuis Settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isRunning      by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val restrictedCount by viewModel.restrictedAppsCount.collectAsStateWithLifecycle()
    val permissions    by viewModel.permissions.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Volt",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.dashboard_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // La carte d'onboarding n'apparaît que si une permission manque.
        AnimatedVisibility(
            visible = !permissions.allGranted,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            OnboardingCard(status = permissions)
        }

        ServiceStatusCard(
            isRunning = isRunning,
            onToggle = viewModel::toggleService
        )

        StatTile(
            value = restrictedCount.toString(),
            label = pluralStringResource(R.plurals.restricted_apps, restrictedCount),
            sub = stringResource(R.string.deep_sleep_savings)
        )
    }
}

// ============================================================== //
// Onboarding — carte d'auto-configuration des permissions critiques
// ============================================================== //

@Composable
private fun OnboardingCard(status: PermissionChecker.PermissionStatus) {
    val context = LocalContext.current

    // Launcher pour POST_NOTIFICATIONS (API 33+)
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* l'état sera recapturé via refreshPermissions au ON_RESUME suivant */ }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.setup_required),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            PermissionRow(
                icon = Icons.Outlined.QueryStats,
                title = stringResource(R.string.perm_usage_title),
                description = stringResource(R.string.perm_usage_desc),
                granted = status.hasUsageStats,
                onAction = {
                    context.startActivity(PermissionChecker.usageStatsSettingsIntent())
                }
            )
            DividerThin()
            PermissionRow(
                icon = Icons.Outlined.BatteryFull,
                title = stringResource(R.string.perm_battery_title),
                description = stringResource(R.string.perm_battery_desc),
                granted = status.isIgnoringBatteryOptimizations,
                onAction = {
                    context.startActivity(
                        PermissionChecker.ignoreBatteryOptimizationsIntent(context.packageName)
                    )
                }
            )
            DividerThin()
            PermissionRow(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.perm_notif_title),
                description = stringResource(R.string.perm_notif_desc),
                granted = status.canPostNotifications,
                onAction = {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            )
            DividerThin()
            // Sur Android 16, Shizuku est l'unique voie pour manipuler les
            // App Standby Buckets (le grant ADB CHANGE_APP_IDLE_STATE est mort).
            PermissionRow(
                icon = Icons.Outlined.Bolt,
                title = stringResource(R.string.shizuku_title),
                description = when (status.shizuku) {
                    ShizukuGateway.Availability.NOT_INSTALLED ->
                        stringResource(R.string.shizuku_desc_not_installed)
                    ShizukuGateway.Availability.INSTALLED_NOT_RUNNING ->
                        stringResource(R.string.shizuku_desc_not_running)
                    ShizukuGateway.Availability.NOT_GRANTED ->
                        stringResource(R.string.shizuku_desc_not_granted)
                    ShizukuGateway.Availability.READY ->
                        stringResource(R.string.shizuku_desc_ready)
                },
                granted = status.shizuku == ShizukuGateway.Availability.READY,
                actionLabel = when (status.shizuku) {
                    ShizukuGateway.Availability.NOT_INSTALLED         -> stringResource(R.string.action_install)
                    ShizukuGateway.Availability.INSTALLED_NOT_RUNNING -> stringResource(R.string.action_open)
                    else                                              -> stringResource(R.string.action_authorize)
                },
                onAction = {
                    when (status.shizuku) {
                        ShizukuGateway.Availability.NOT_INSTALLED ->
                            runCatching {
                                context.startActivity(PermissionChecker.shizukuFdroidIntent())
                            }
                        ShizukuGateway.Availability.INSTALLED_NOT_RUNNING ->
                            runCatching {
                                context.packageManager
                                    .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ?.let(context::startActivity)
                            }
                        ShizukuGateway.Availability.NOT_GRANTED ->
                            runCatching { rikka.shizuku.Shizuku.requestPermission(0x5A12) }
                        ShizukuGateway.Availability.READY -> Unit
                    }
                }
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String = stringResource(R.string.action_grant),
    onAction: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = if (granted) SignalOk else SignalWarning,
        animationSpec = tween(300),
        label = "permRow"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(12.dp))
        if (!granted) {
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DividerThin() = HorizontalDivider(
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
    thickness = 0.5.dp
)

// ============================================================== //
// Composants existants — statut service + tile métrique
// ============================================================== //

@Composable
private fun ServiceStatusCard(
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val targetColor by animateColorAsState(
        targetValue = if (isRunning) SignalOk else SignalError,
        animationSpec = tween(400),
        label = "statusDot"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(color = targetColor)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (isRunning) R.string.service_active else R.string.service_inactive),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(if (isRunning) R.string.service_active_desc else R.string.service_inactive_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isRunning, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun StatTile(value: String, label: String, sub: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
