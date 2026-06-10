package com.tonnomdeved.volt.ui.screens.hibernate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tonnomdeved.volt.R
import com.tonnomdeved.volt.data.hibernation.HibernationLevel
import com.tonnomdeved.volt.data.hibernation.HibernationResult
import com.tonnomdeved.volt.data.hibernation.nocivity.NocivityBreakdown
import com.tonnomdeved.volt.data.hibernation.whitelist.WhitelistReason
import com.tonnomdeved.volt.ui.screens.hibernate.HibernateViewModel.AppHibernationItem
import com.tonnomdeved.volt.ui.screens.hibernate.HibernateViewModel.Filter
import com.tonnomdeved.volt.ui.screens.hibernate.HibernateViewModel.Sort
import com.tonnomdeved.volt.ui.theme.SignalError
import com.tonnomdeved.volt.ui.theme.SignalOk
import com.tonnomdeved.volt.ui.theme.SignalWarning
import kotlinx.coroutines.launch

@Composable
fun HibernateScreen(
    contentPadding: PaddingValues,
    viewModel: HibernateViewModel = viewModel()
) {
    val loading      by viewModel.loading.collectAsStateWithLifecycle()
    val visibleItems by viewModel.visibleItems.collectAsStateWithLifecycle()
    val suggestions  by viewModel.suggestions.collectAsStateWithLifecycle()
    val filter       by viewModel.filter.collectAsStateWithLifecycle()
    val sort         by viewModel.sort.collectAsStateWithLifecycle()
    val hibCount     by viewModel.hibernatedCount.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuAvailability.collectAsStateWithLifecycle()
    val savings      by viewModel.savings.collectAsStateWithLifecycle()
    val autoEnabled  by viewModel.autoEnabled.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var detailItem by remember { mutableStateOf<AppHibernationItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            // ----- En-tête + Hero stat ----- //
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.tab_hibernate),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.hibernate_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HeroStatCard(
                hibernatedCount = hibCount,
                savings = savings,
                onWakeAll = {
                    viewModel.wakeAll { woke ->
                        scope.launch {
                            snackbarHost.showSnackbar(
                                if (woke > 0) context.getString(R.string.snack_woke, woke)
                                else context.getString(R.string.snack_nothing_to_wake)
                            )
                        }
                    }
                }
            )

            AutoHibernationCard(
                enabled = autoEnabled,
                onToggle = { on ->
                    viewModel.setAutoHibernation(on) { changed ->
                        if (on) scope.launch {
                            snackbarHost.showSnackbar(
                                if (changed > 0) context.getString(R.string.snack_auto_on_changed, changed)
                                else context.getString(R.string.snack_auto_on)
                            )
                        }
                    }
                },
                onRunNow = {
                    viewModel.runAutoNow { changed ->
                        scope.launch {
                            snackbarHost.showSnackbar(
                                if (changed > 0) context.getString(R.string.snack_changed, changed)
                                else context.getString(R.string.snack_nothing_to_hibernate)
                            )
                        }
                    }
                }
            )

            // ----- Onboarding Shizuku ----- //
            AnimatedVisibility(visible = shizukuState != com.tonnomdeved.volt.data.hibernation.shizuku.ShizukuGateway.Availability.READY) {
                HibernateShizukuOnboarding(
                    availability = shizukuState,
                    onCheckAgain = { viewModel.refreshShizukuAvailability() }
                )
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // ----- Suggestions ----- //
                    if (suggestions.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.section_suggestions)) }
                        item {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.applySuggestedAll { applied, _ ->
                                        scope.launch {
                                            snackbarHost.showSnackbar(
                                                context.getString(R.string.snack_changed, applied)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                            ) {
                                Text(stringResource(R.string.hibernate_n_suggestions, suggestions.size))
                            }
                        }
                        items(suggestions, key = { "sugg-" + it.app.packageName }) { item ->
                            SuggestionCard(
                                item = item,
                                onTap = { detailItem = item },
                                onHibernate = { lvl ->
                                    viewModel.applyLevel(item.app.packageName, lvl) { res ->
                                        scope.launch { snackbarHost.showSnackbar(humanReadable(context, res)) }
                                    }
                                }
                            )
                        }
                    }

                    // ----- Filtre + tri ----- //
                    item { SectionHeader(stringResource(R.string.section_all_apps)) }
                    item {
                        FilterSortBar(
                            filter = filter,
                            sort = sort,
                            onFilterChange = viewModel::setFilter,
                            onSortChange = viewModel::setSort
                        )
                    }

                    // ----- Liste complète ----- //
                    items(visibleItems, key = { "all-" + it.app.packageName }) { item ->
                        AppHibernationRow(
                            item = item,
                            onTap = { detailItem = item },
                            onLevelChange = { lvl ->
                                viewModel.applyLevel(item.app.packageName, lvl) { res ->
                                    scope.launch { snackbarHost.showSnackbar(humanReadable(context, res)) }
                                }
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        )
    }

    // ----- Bottom sheet détail ----- //
    detailItem?.let { item ->
        HibernateAppDetailSheet(
            item = item,
            onDismiss = { detailItem = null },
            onApplyLevel = { lvl ->
                viewModel.applyLevel(item.app.packageName, lvl) { res ->
                    scope.launch { snackbarHost.showSnackbar(humanReadable(context, res)) }
                }
                detailItem = null
            },
            onTogglePin = { pinned ->
                viewModel.togglePin(item.app.packageName, pinned)
                detailItem = null
            }
        )
    }
}

// ============================================================== //
// Sous-composants
// ============================================================== //

@Composable
private fun HeroStatCard(
    hibernatedCount: Int,
    savings: com.tonnomdeved.volt.data.hibernation.SavingsEstimator.Savings,
    onWakeAll: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = hibernatedCount.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pluralStringResource(R.plurals.hibernated_apps, hibernatedCount),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (hibernatedCount > 0) {
                    androidx.compose.material3.TextButton(onClick = onWakeAll) {
                        Text(stringResource(R.string.wake_all))
                    }
                }
            }

            // Estimation d'économie batterie (indicative, jamais présentée comme exacte)
            if (savings.appCount > 0 && savings.dailyPercent >= 0.5) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SignalOk.copy(alpha = 0.12f))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.savings_headline, savings.dailyPercent.toInt()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SignalOk
                        )
                        Text(
                            text = stringResource(R.string.savings_detail, savings.dailyMah, savings.extraStandbyMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.wake_on_push_active),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AutoHibernationCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_hibernation),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.auto_hibernation_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(
                onClick = onRunNow,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.run_pass_now))
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SuggestionCard(
    item: AppHibernationItem,
    onTap: () -> Unit,
    onHibernate: (HibernationLevel) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable { onTap() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconSmall(item)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.size(8.dp))
                    ScoreBadge(item.score)
                }
                Text(
                    text = stringResource(R.string.inactive_days, item.score.daysSinceLastUse) +
                           if (item.score.backgroundBytesLast7d > 1024 * 1024)
                               " · ${formatMb(item.score.backgroundBytesLast7d)}"
                           else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(8.dp))
            FilledTonalButton(onClick = {
                onHibernate(
                    when {
                        item.score.total >= 85 -> HibernationLevel.HARD
                        item.score.total >= 60 -> HibernationLevel.MEDIUM
                        else                   -> HibernationLevel.SOFT
                    }
                )
            }) {
                Text(stringResource(R.string.hibernate_action))
            }
        }
    }
}

@Composable
private fun FilterSortBar(
    filter: Filter,
    sort: Sort,
    onFilterChange: (Filter) -> Unit,
    onSortChange: (Sort) -> Unit
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Filter.entries.forEach { f ->
            FilterChip(
                selected = filter == f,
                onClick = { onFilterChange(f) },
                label = { Text(filterLabel(context, f)) }
            )
        }
        Spacer(Modifier.weight(1f))
        Box {
            AssistChip(
                onClick = { sortMenuOpen = true },
                label = { Text(stringResource(R.string.sort_prefix, sortLabel(context, sort))) }
            )
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false }
            ) {
                Sort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(context, s)) },
                        onClick = { onSortChange(s); sortMenuOpen = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppHibernationRow(
    item: AppHibernationItem,
    onTap: () -> Unit,
    onLevelChange: (HibernationLevel) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconSmall(item)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.app.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (item.score.daysSinceLastUse == 0) stringResource(R.string.active_today)
                       else stringResource(R.string.inactive_days, item.score.daysSinceLastUse),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(8.dp))
        if (item.isProtected) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = stringResource(R.string.protected_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ScoreBadge(item.score)
            Spacer(Modifier.size(8.dp))
            Box {
                AssistChip(
                    onClick = { menuOpen = true },
                    label = { Text(levelLabel(context, item.currentLevel)) }
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    HibernationLevel.entries.forEach { lvl ->
                        DropdownMenuItem(
                            text = { Text(levelLabel(context, lvl)) },
                            onClick = { menuOpen = false; onLevelChange(lvl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconSmall(item: AppHibernationItem) {
    val shape = RoundedCornerShape(12.dp)
    if (item.app.icon != null) {
        Image(
            bitmap = item.app.icon,
            contentDescription = item.app.label,
            modifier = Modifier.size(40.dp).clip(shape)
        )
    } else {
        Box(
            modifier = Modifier.size(40.dp).clip(shape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreBadge(score: NocivityBreakdown) {
    val target = severityColor(score.severity)
    val animated by animateColorAsState(targetValue = target, animationSpec = tween(400), label = "score")
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(animated.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = score.total.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = animated,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================== //
// Helpers
// ============================================================== //

private fun severityColor(s: NocivityBreakdown.Severity): Color = when (s) {
    NocivityBreakdown.Severity.CALM     -> SignalOk
    NocivityBreakdown.Severity.MODERATE -> SignalWarning
    NocivityBreakdown.Severity.HIGH     -> Color(0xFFFF8A00)
    NocivityBreakdown.Severity.CRITICAL -> SignalError
}

private fun filterLabel(ctx: android.content.Context, f: Filter): String = ctx.getString(when (f) {
    Filter.ALL        -> R.string.filter_all
    Filter.SUGGESTED  -> R.string.filter_suggested
    Filter.HIBERNATED -> R.string.filter_hibernated
    Filter.PROTECTED  -> R.string.filter_protected
})

private fun sortLabel(ctx: android.content.Context, s: Sort): String = ctx.getString(when (s) {
    Sort.SCORE_DESC    -> R.string.sort_score
    Sort.LAST_USED_ASC -> R.string.sort_inactivity
    Sort.NAME_ASC      -> R.string.sort_name
})

internal fun levelLabel(ctx: android.content.Context, l: HibernationLevel): String = ctx.getString(when (l) {
    HibernationLevel.OFF    -> R.string.level_off
    HibernationLevel.SOFT   -> R.string.level_soft
    HibernationLevel.MEDIUM -> R.string.level_medium
    HibernationLevel.HARD   -> R.string.level_hard
})

internal fun protectionLabel(ctx: android.content.Context, reason: WhitelistReason): String = when (reason) {
    is WhitelistReason.SystemApp              -> ctx.getString(R.string.prot_system)
    is WhitelistReason.HoldsRole              -> ctx.getString(R.string.prot_role, reason.roleName.substringAfterLast('.'))
    is WhitelistReason.ActiveInputMethod      -> ctx.getString(R.string.prot_ime)
    is WhitelistReason.ActiveAccessibilityService -> ctx.getString(R.string.prot_a11y)
    is WhitelistReason.ActiveVpn              -> ctx.getString(R.string.prot_vpn)
    is WhitelistReason.DeviceAdmin            -> ctx.getString(R.string.prot_admin)
    is WhitelistReason.LiveWallpaper          -> ctx.getString(R.string.prot_wallpaper)
    is WhitelistReason.NotificationListener   -> ctx.getString(R.string.prot_notif_listener)
    is WhitelistReason.DeclaresAutofill       -> ctx.getString(R.string.prot_autofill)
    is WhitelistReason.CuratedSafetyList      -> ctx.getString(R.string.prot_curated)
    is WhitelistReason.UserPinned             -> ctx.getString(R.string.prot_pinned)
}

private fun humanReadable(ctx: android.content.Context, result: HibernationResult): String = when (result) {
    HibernationResult.Success     -> ctx.getString(R.string.snack_policy_applied)
    HibernationResult.Unchanged   -> ctx.getString(R.string.snack_no_change)
    is HibernationResult.Blocked  -> ctx.getString(R.string.snack_blocked, protectionLabel(ctx, result.reason))
    is HibernationResult.ShizukuUnavailable -> ctx.getString(R.string.snack_shizuku_required, levelLabel(ctx, result.requested))
    is HibernationResult.Failed   -> ctx.getString(R.string.snack_failed, result.error.javaClass.simpleName)
}

private fun formatMb(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 100.0) "${mb.toInt()} MB"
           else "%.1f MB".format(mb)
}
