package com.tonnomdeved.volt.ui.screens.hibernate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.tonnomdeved.volt.R
import androidx.compose.ui.unit.dp
import com.tonnomdeved.volt.data.hibernation.HibernationLevel
import com.tonnomdeved.volt.data.hibernation.nocivity.NocivityBreakdown
import com.tonnomdeved.volt.ui.screens.hibernate.HibernateViewModel.AppHibernationItem

/**
 * Bottom sheet de détail / configuration d'une app.
 *
 * Expose :
 *  - 5 mini-jauges (décomposition transparente du score)
 *  - Radio 4 niveaux (OFF / SOFT / MEDIUM / HARD), HARD désactivé si protégée
 *  - Switch "Épingler" (= userPinned, protège l'app)
 *
 * Le bouton "Appliquer" ferme le sheet et déclenche `onApplyLevel`.
 * Le toggle "Épingler" déclenche immédiatement `onTogglePin` et ferme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HibernateAppDetailSheet(
    item: AppHibernationItem,
    onDismiss: () -> Unit,
    onApplyLevel: (HibernationLevel) -> Unit,
    onTogglePin: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedLevel by remember { mutableStateOf(item.currentLevel) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ----- En-tête ----- //
            Column {
                Text(
                    text = item.app.label,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ----- Bandeau de protection si applicable ----- //
            item.protection?.let { reason ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_protected),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = protectionLabel(LocalContext.current, reason),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ----- Score breakdown ----- //
            Text(
                text = stringResource(R.string.nocivity_score, item.score.total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            ScoreBreakdown(score = item.score)

            // ----- Niveaux ----- //
            Text(
                text = stringResource(R.string.hibernation_level),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            HibernationLevel.entries.forEach { lvl ->
                LevelOption(
                    level = lvl,
                    selected = selectedLevel == lvl,
                    enabled = !item.isProtected || lvl == HibernationLevel.OFF,
                    onClick = { selectedLevel = lvl }
                )
            }

            // ----- Épinglage ----- //
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pin_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.pin_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = item.userPinned,
                    onCheckedChange = { onTogglePin(it) }
                )
            }

            Spacer(Modifier.height(4.dp))

            FilledTonalButton(
                onClick = { onApplyLevel(selectedLevel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !item.isProtected || selectedLevel == HibernationLevel.OFF
            ) {
                Text(stringResource(R.string.apply))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScoreBreakdown(score: NocivityBreakdown) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScoreRow(stringResource(R.string.comp_inactivity), score.inactivity,        30)
        ScoreRow(stringResource(R.string.comp_background), score.backgroundRatio,   25)
        ScoreRow(stringResource(R.string.comp_wakeups),    score.wakeups,           20)
        ScoreRow(stringResource(R.string.comp_network),    score.networkBackground, 15)
        ScoreRow(stringResource(R.string.comp_battery),    score.batteryImpact,     10)
    }
}

@Composable
private fun ScoreRow(label: String, value: Int?, max: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (value != null) {
                LinearProgressIndicator(
                    progress = { value.toFloat() / max },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = if (value == null) "—" else "$value / $max",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LevelOption(
    level: HibernationLevel,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = if (enabled) onClick else null,
            enabled = enabled
        )
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = levelLabel(LocalContext.current, level),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = stringResource(levelDescriptionRes(level)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun levelDescriptionRes(l: HibernationLevel): Int = when (l) {
    HibernationLevel.OFF    -> R.string.level_off_desc
    HibernationLevel.SOFT   -> R.string.level_soft_desc
    HibernationLevel.MEDIUM -> R.string.level_medium_desc
    HibernationLevel.HARD   -> R.string.level_hard_desc
}
