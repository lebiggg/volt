package com.tonnomdeved.volt.ui.screens.push

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonnomdeved.volt.data.VoltPreferences
import com.tonnomdeved.volt.data.VoltStateBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PushConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = VoltPreferences(application)

    private val _urlDraft = MutableStateFlow("")
    /** Texte courant tapé par l'utilisateur — pas encore persisté. */
    val urlDraft: StateFlow<String> = _urlDraft.asStateFlow()

    /** URL persistée (dernière sauvegarde). */
    val savedUrl: StateFlow<String> = prefs.pushServerUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Statut WebSocket émis par le PushConnectionManager via VoltStateBus. */
    val pushStatus: StateFlow<VoltStateBus.PushStatus> = VoltStateBus.pushStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
                 VoltStateBus.PushStatus.Disconnected)

    init {
        viewModelScope.launch {
            prefs.pushServerUrl.collect { persisted ->
                // Pré-remplir le champ au démarrage uniquement
                if (_urlDraft.value.isEmpty()) _urlDraft.value = persisted
            }
        }
    }

    fun onUrlChange(newValue: String) { _urlDraft.value = newValue }

    fun saveUrl() {
        viewModelScope.launch {
            prefs.setPushServerUrl(_urlDraft.value)
        }
    }

    /** Validation basique — accepte uniquement wss:// (TLS 1.3 obligatoire — cf. spec §IV). */
    fun isUrlValid(value: String = _urlDraft.value): Boolean =
        value.startsWith("wss://") && value.length > "wss://".length
}
