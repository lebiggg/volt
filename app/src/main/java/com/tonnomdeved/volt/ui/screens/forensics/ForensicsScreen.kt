package com.tonnomdeved.volt.ui.screens.forensics

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tonnomdeved.volt.R
import com.tonnomdeved.volt.data.forensics.AppNightActivity
import com.tonnomdeved.volt.data.forensics.NightReport
import com.tonnomdeved.volt.ui.theme.SignalError
import com.tonnomdeved.volt.ui.theme.SignalOk
import com.tonnomdeved.volt.ui.theme.SignalWarning
import kotlinx.coroutines.launch

@Composable
fun ForensicsScreen(
    contentPadding: PaddingValues,
    viewModel: ForensicsViewModel = viewModel()
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val report  by viewModel.report.collectAsStateWithLifecycle()
    val windowH by viewModel.windowHours.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.tab_forensics),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.forensics_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Carte de contrôle : fenêtre + scan
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.forensics_window, windowH),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4, 8, 12, 24).forEach { h ->
                            androidx.compose.material3.FilterChip(
                                selected = windowH == h,
                                onClick = { viewModel.setWindowHours(h) },
                                label = { Text("${h}h") }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = { viewModel.scan() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    ) {
                        Text(stringResource(R.string.forensics_scan))
                    }
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                report == null -> EmptyState()
                else -> ReportList(
                    report = report!!,
                    onHibernate = { pkg ->
                        viewModel.hibernate(pkg) { ok ->
                            scope.launch {
                                snackbarHost.showSnackbar(
                                    if (ok) context.getString(R.string.snack_policy_applied)
                                    else context.getString(R.string.forensics_hibernate_failed)
                                )
                            }
                        }
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.forensics_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReportList(report: NightReport, onHibernate: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (!report.wakelockDataAvailable) {
                    Text(
                        text = stringResource(R.string.forensics_no_wakelock),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SignalWarning
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(
                        R.string.forensics_summary,
                        report.apps.size,
                        report.totalWakeups
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (report.apps.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.forensics_quiet_night),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SignalOk
                    )
                }
            }
        } else {
            items(report.apps, key = { it.packageName }) { app ->
                CulpritRow(app = app, onHibernate = { onHibernate(app.packageName) })
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun CulpritRow(app: AppNightActivity, onHibernate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImpactBadge(app.severity, app.impact)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildMetrics(app),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(8.dp))
        TextButton(onClick = onHibernate) {
            Text(stringResource(R.string.hibernate_action))
        }
    }
}

@Composable
private fun buildMetrics(app: AppNightActivity): String {
    val parts = mutableListOf<String>()
    if (app.wakeups != null) {
        parts += stringResource(R.string.forensics_metric_wakeups, app.wakeups)
    }
    if (app.foregroundEvents > 0) {
        parts += stringResource(R.string.forensics_metric_foreground, app.foregroundEvents)
    }
    if (app.backgroundBytes > 1024 * 1024) {
        val mb = app.backgroundBytes / (1024.0 * 1024.0)
        parts += stringResource(R.string.forensics_metric_network, mb.toInt())
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ImpactBadge(severity: AppNightActivity.Severity, impact: Int) {
    val color = when (severity) {
        AppNightActivity.Severity.HIGH     -> SignalError
        AppNightActivity.Severity.MODERATE -> SignalWarning
        AppNightActivity.Severity.LOW      -> SignalOk
    }
    val animated by animateColorAsState(targetValue = color, label = "impact")
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(animated.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = impact.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = animated,
            fontWeight = FontWeight.SemiBold
        )
    }
}
