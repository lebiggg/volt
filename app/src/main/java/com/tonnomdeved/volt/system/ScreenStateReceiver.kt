package com.tonnomdeved.volt.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tonnomdeved.volt.BuildConfig

/**
 * Receiver d'état de l'écran — enregistré dynamiquement par
 * [com.tonnomdeved.volt.BatteryCommandService] (les broadcasts
 * ACTION_SCREEN_ON/OFF ne sont pas autorisés en static depuis API 26).
 *
 * Logs gardés en `Log.d` mais conditionnés à `BuildConfig.DEBUG` →
 * release builds n'écrivent rien dans logcat.
 */
class ScreenStateReceiver(
    private val onScreenStateChanged: (isScreenOn: Boolean) -> Unit
) : BroadcastReceiver() {

    private companion object { private const val TAG = "VoltScreenReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Screen OFF → Deep Sleep ON")
                onScreenStateChanged(false)
            }
            Intent.ACTION_SCREEN_ON -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Screen ON → Deep Sleep OFF")
                onScreenStateChanged(true)
            }
        }
    }
}
