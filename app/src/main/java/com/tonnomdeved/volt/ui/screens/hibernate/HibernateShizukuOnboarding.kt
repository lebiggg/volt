package com.tonnomdeved.volt.ui.screens.hibernate

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.tonnomdeved.volt.R
import androidx.compose.ui.unit.dp
import com.tonnomdeved.volt.data.hibernation.shizuku.ShizukuGateway

/**
 * Carte d'onboarding Shizuku — apparaît tant que [availability] != READY.
 *
 * Adapte le CTA selon l'état :
 *  - NOT_INSTALLED         → "Installer Shizuku" (F-Droid)
 *  - INSTALLED_NOT_RUNNING → "Démarrer Shizuku"
 *  - NOT_GRANTED           → "Accorder l'autorisation"
 */
@Composable
fun HibernateShizukuOnboarding(
    availability: ShizukuGateway.Availability,
    onCheckAgain: () -> Unit
) {
    val context = LocalContext.current

    val (title, description, cta, action) = when (availability) {
        ShizukuGateway.Availability.NOT_INSTALLED -> StepConfig(
            title = stringResource(R.string.shizuku_card_enable_hard),
            description = stringResource(R.string.shizuku_card_not_installed_desc),
            ctaLabel = stringResource(R.string.shizuku_card_install),
            action = {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://f-droid.org/packages/moe.shizuku.privileged.api/"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        )
        ShizukuGateway.Availability.INSTALLED_NOT_RUNNING -> StepConfig(
            title = stringResource(R.string.shizuku_card_start_title),
            description = stringResource(R.string.shizuku_card_start_desc),
            ctaLabel = stringResource(R.string.shizuku_card_open),
            action = {
                runCatching {
                    val intent = context.packageManager
                        .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent?.let { context.startActivity(it) }
                }
            }
        )
        ShizukuGateway.Availability.NOT_GRANTED -> StepConfig(
            title = stringResource(R.string.shizuku_card_grant_title),
            description = stringResource(R.string.shizuku_card_grant_desc),
            ctaLabel = stringResource(R.string.shizuku_card_request),
            action = {
                runCatching { rikka.shizuku.Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) }
            }
        )
        ShizukuGateway.Availability.READY -> return  // ne s'affiche pas
    }

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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = { action(); onCheckAgain() },
                    modifier = Modifier.weight(1f)
                ) { Text(cta) }
                TextButton(onClick = onCheckAgain) { Text(stringResource(R.string.shizuku_card_recheck)) }
            }
        }
    }
}

private data class StepConfig(
    val title: String,
    val description: String,
    val ctaLabel: String,
    val action: () -> Unit
)

private const val SHIZUKU_REQUEST_CODE = 0x5A12  // arbitraire — unique dans Volt
