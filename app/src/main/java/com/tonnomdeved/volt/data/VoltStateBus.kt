package com.tonnomdeved.volt.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bus d'état global, observable depuis la UI.
 *
 * N'expose **que** des [StateFlow] immuables côté lecture (`val`).
 * Seul le moteur (BatteryCommandService / PushConnectionManager /
 * HibernationController) doit muter ces flux via les méthodes `update*`.
 * Respecte l'invariant Thread-Safety de `volt_architecture_spec.md` §IV
 * et évite tout couplage direct UI ↔ Service.
 */
object VoltStateBus {

    // ---------------------------------------------------------------- //
    // Statut du service (Foreground Service actif/inactif)
    // ---------------------------------------------------------------- //
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()
    fun updateServiceRunning(running: Boolean) { _serviceRunning.value = running }

    // ---------------------------------------------------------------- //
    // Statut WebSocket UnifiedPush
    // ---------------------------------------------------------------- //
    sealed interface PushStatus {
        data object Disconnected : PushStatus
        data object Connecting : PushStatus
        data object Connected : PushStatus
        /** Reconnexion programmée — temps restant en secondes (backoff exponentiel). */
        data class Backoff(val nextRetryInSec: Long) : PushStatus
        data class Error(val message: String) : PushStatus
    }

    private val _pushStatus = MutableStateFlow<PushStatus>(PushStatus.Disconnected)
    val pushStatus: StateFlow<PushStatus> = _pushStatus.asStateFlow()
    fun updatePushStatus(status: PushStatus) { _pushStatus.value = status }

    // ---------------------------------------------------------------- //
    // Compteur d'apps actuellement restreintes (Deep Sleep — legacy)
    // ---------------------------------------------------------------- //
    private val _restrictedAppsCount = MutableStateFlow(0)
    val restrictedAppsCount: StateFlow<Int> = _restrictedAppsCount.asStateFlow()
    fun updateRestrictedAppsCount(count: Int) { _restrictedAppsCount.value = count }

    // ---------------------------------------------------------------- //
    // Compteur d'apps hibernées (P1 Hibernate)
    //
    // Sémantique distincte de restrictedAppsCount :
    //   - restrictedAppsCount = compte transitoire (au moment du screen-off)
    //   - hibernatedAppsCount = compte persistant (politiques level != OFF)
    // Alimenté par le BatteryCommandService qui observe
    // HibernationRepository.activeCount.
    // ---------------------------------------------------------------- //
    private val _hibernatedAppsCount = MutableStateFlow(0)
    val hibernatedAppsCount: StateFlow<Int> = _hibernatedAppsCount.asStateFlow()
    fun updateHibernatedAppsCount(count: Int) { _hibernatedAppsCount.value = count }
}
