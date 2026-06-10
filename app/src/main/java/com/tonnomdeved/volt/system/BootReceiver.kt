package com.tonnomdeved.volt.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.tonnomdeved.volt.BatteryCommandService
import com.tonnomdeved.volt.BuildConfig
import com.tonnomdeved.volt.data.VoltPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Démarre le [BatteryCommandService] au boot — **uniquement si l'utilisateur
 * l'a explicitement activé** dans les réglages (`startOnBoot`).
 *
 * Opt-in strict : par défaut, rien ne se lance au boot. C'est un choix de
 * respect — l'utilisateur GrapheneOS décide ce qui démarre sur son appareil.
 *
 * `goAsync()` permet de lire la préférence DataStore (suspendue) sans bloquer
 * le thread du receiver. Le service n'est démarré qu'après lecture confirmée.
 */
class BootReceiver : BroadcastReceiver() {

    private companion object { private const val TAG = "VoltBoot" }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val enabled = VoltPreferences(appContext).startOnBoot.first()
                if (enabled) {
                    val svc = Intent(appContext, BatteryCommandService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(svc)
                    } else {
                        appContext.startService(svc)
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "service démarré au boot")
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "boot start failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
