package com.tonnomdeved.volt.ui.screens.dashboard

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonnomdeved.volt.BatteryCommandService
import com.tonnomdeved.volt.VoltApplication
import com.tonnomdeved.volt.data.PermissionChecker
import com.tonnomdeved.volt.data.VoltStateBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Centralise :
 *  - le pilotage start/stop du [BatteryCommandService] ;
 *  - l'exposition de l'état moteur (via [VoltStateBus]) ;
 *  - le snapshot des permissions critiques pour l'onboarding.
 *
 * Le snapshot des permissions n'est pas un Flow réactif : Android ne pousse
 * pas d'event quand l'utilisateur change un toggle dans Settings. La UI doit
 * appeler [refreshPermissions] à chaque `ON_RESUME` de l'Activity.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val permissionChecker = PermissionChecker(
        application,
        (application as VoltApplication).container.shizukuGateway
    )

    val serviceRunning: StateFlow<Boolean> = VoltStateBus.serviceRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val restrictedAppsCount: StateFlow<Int> = VoltStateBus.restrictedAppsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _permissions = MutableStateFlow(permissionChecker.snapshot())
    val permissions: StateFlow<PermissionChecker.PermissionStatus> = _permissions.asStateFlow()

    /** À appeler depuis l'Activity sur ON_RESUME — capture les changements faits dans Settings. */
    fun refreshPermissions() {
        _permissions.value = permissionChecker.snapshot()
    }

    fun toggleService(enable: Boolean) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, BatteryCommandService::class.java)
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } else {
            ctx.stopService(intent)
        }
    }
}
