package com.tonnomdeved.volt.ui.screens.push

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tonnomdeved.volt.data.VoltStateBus
import com.tonnomdeved.volt.ui.theme.SignalError
import com.tonnomdeved.volt.ui.theme.SignalOk
import com.tonnomdeved.volt.ui.theme.SignalWarning

@Composable
fun PushConfigScreen(
    contentPadding: PaddingValues,
    viewModel: PushConfigViewModel = viewModel()
) {
    val urlDraft by viewModel.urlDraft.collectAsStateWithLifecycle()
    val status   by viewModel.pushStatus.collectAsStateWithLifecycle()
    val canSave  = viewModel.isUrlValid(urlDraft)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Serveur Push",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Renseignez l'URL WebSocket de votre distributeur UnifiedPush " +
                   "(Gotify, Ntfy, NextPush…). TLS 1.3 requis.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = urlDraft,
            onValueChange = viewModel::onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL du serveur") },
            placeholder = { Text("wss://push.exemple.org/ws") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            isError = urlDraft.isNotEmpty() && !canSave,
            supportingText = {
                if (urlDraft.isNotEmpty() && !canSave) {
                    Text("L'URL doit commencer par wss://")
                }
            }
        )

        FilledTonalButton(
            onClick = viewModel::saveUrl,
            enabled = canSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enregistrer", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(4.dp))

        ConnectionStatusCard(status)
    }
}

@Composable
private fun ConnectionStatusCard(status: VoltStateBus.PushStatus) {
    val (label, detail, color) = when (status) {
        VoltStateBus.PushStatus.Connected    ->
            Triple("Connecté",     "Tunnel WebSocket actif.",                SignalOk)
        VoltStateBus.PushStatus.Connecting   ->
            Triple("Connexion…",   "Négociation TLS en cours.",              SignalWarning)
        VoltStateBus.PushStatus.Disconnected ->
            Triple("Déconnecté",   "Aucune connexion active.",               SignalError)
        is VoltStateBus.PushStatus.Backoff   ->
            Triple("En attente",
                   "Reconnexion dans ${status.nextRetryInSec}s (backoff exponentiel).",
                   SignalWarning)
        is VoltStateBus.PushStatus.Error     ->
            Triple("Erreur",       status.message,                            SignalError)
    }

    val animatedColor by animateColorAsState(targetValue = color, label = "pushStatus")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(color = animatedColor)
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}
