package com.tonnomdeved.volt.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tonnomdeved.volt.data.VoltPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = VoltPreferences(application)

    val theme: StateFlow<VoltPreferences.ThemeMode> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoltPreferences.ThemeMode.SYSTEM)
    val dynamicColor: StateFlow<Boolean> = prefs.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val soft: StateFlow<Int> = prefs.thresholdSoft
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)
    val medium: StateFlow<Int> = prefs.thresholdMedium
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 60)
    val hard: StateFlow<Int> = prefs.thresholdHard
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 85)

    fun setTheme(mode: VoltPreferences.ThemeMode) { viewModelScope.launch { prefs.setThemeMode(mode) } }
    fun setDynamic(enabled: Boolean) { viewModelScope.launch { prefs.setDynamicColor(enabled) } }
    fun setThresholds(s: Int, m: Int, h: Int) { viewModelScope.launch { prefs.setThresholds(s, m, h) } }
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenGithub: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val theme   by viewModel.theme.collectAsStateWithLifecycle()
    val dynamic by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val soft    by viewModel.soft.collectAsStateWithLifecycle()
    val medium  by viewModel.medium.collectAsStateWithLifecycle()
    val hard    by viewModel.hard.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Réglages", style = MaterialTheme.typography.headlineMedium)

        // ---- Apparence ----
        SettingsCard(title = "Apparence") {
            Text("Thème", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoltPreferences.ThemeMode.entries.forEach { mode ->
                    androidx.compose.material3.FilterChip(
                        selected = theme == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(themeLabel(mode)) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ToggleRow(
                title = "Couleurs dynamiques",
                subtitle = "Material You — suit le fond d'écran (Android 12+)",
                checked = dynamic,
                onChange = viewModel::setDynamic
            )
        }

        // ---- Seuils d'auto-hibernation ----
        SettingsCard(title = "Seuils d'auto-hibernation") {
            Text(
                "Score de nocivité à partir duquel chaque niveau s'applique automatiquement.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            ThresholdSlider("Soft",   soft,   0..100)   { viewModel.setThresholds(it, medium, hard) }
            ThresholdSlider("Medium", medium, soft..100) { viewModel.setThresholds(soft, it, hard) }
            ThresholdSlider("Hard",   hard,   medium..100) { viewModel.setThresholds(soft, medium, it) }
        }

        // ---- À propos ----
        SettingsCard(title = "À propos") {
            InfoRow("Version", "0.2.0-alpha")
            InfoRow("Licence", "GPL-3.0")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGithub() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Code source sur GitHub",
                     style = MaterialTheme.typography.titleMedium,
                     color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThresholdSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleMedium,
                 fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(value.toString(), style = MaterialTheme.typography.titleMedium,
                 color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(key, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

private fun themeLabel(m: VoltPreferences.ThemeMode): String = when (m) {
    VoltPreferences.ThemeMode.SYSTEM -> "Système"
    VoltPreferences.ThemeMode.LIGHT  -> "Clair"
    VoltPreferences.ThemeMode.DARK   -> "Sombre"
}
