package com.tonnomdeved.volt.data

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.PowerManager
import android.provider.Settings
import com.tonnomdeved.volt.data.hibernation.shizuku.ShizukuGateway

/**
 * Vérificateur d'état des permissions critiques de Volt.
 *
 * Sans état, sans dépendances Lifecycle — peut être appelé depuis n'importe
 * quel ViewModel ou Composable (en `onResume`) pour produire un snapshot
 * immuable `PermissionStatus`. La UI décide ensuite quoi afficher.
 *
 * **Aucune action de demande n'est faite ici** : seulement de l'inspection.
 * Les `Intent` de redirection vers Settings sont fournis par les fonctions
 * factory en bas de fichier, qui restent pures (création d'objet sans I/O).
 *
 * **Note Android 16** : l'ancien chemin "grant ADB de CHANGE_APP_IDLE_STATE"
 * est mort — la permission n'est plus accordable par `pm grant`
 * (`not a changeable permission type`). L'unique voie applicative pour
 * manipuler les App Standby Buckets est désormais **Shizuku**, d'où le
 * champ [PermissionStatus.shizuku] qui remplace l'ancien booléen ADB.
 */
class PermissionChecker(
    private val context: Context,
    private val shizukuGateway: ShizukuGateway
) {

    /** Snapshot immuable des 4 prérequis critiques. */
    data class PermissionStatus(
        val hasUsageStats: Boolean,
        val isIgnoringBatteryOptimizations: Boolean,
        val canPostNotifications: Boolean,
        val shizuku: ShizukuGateway.Availability
    ) {
        val allGranted: Boolean = hasUsageStats &&
                                  isIgnoringBatteryOptimizations &&
                                  canPostNotifications &&
                                  shizuku == ShizukuGateway.Availability.READY
    }

    fun snapshot(): PermissionStatus = PermissionStatus(
        hasUsageStats = hasUsageStatsAccess(),
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
        canPostNotifications = canPostNotifications(),
        shizuku = shizukuGateway.checkAvailability()
    )

    // ---------------------------------------------------------------- //
    // PACKAGE_USAGE_STATS — special access via AppOps
    // ---------------------------------------------------------------- //
    private fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // ---------------------------------------------------------------- //
    // Ignore Battery Optimizations — Doze whitelist
    // ---------------------------------------------------------------- //
    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // ---------------------------------------------------------------- //
    // POST_NOTIFICATIONS — runtime permission depuis Android 13 (API 33)
    // ---------------------------------------------------------------- //
    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    // ---------------------------------------------------------------- //
    // Intents factory — création pure d'Intent, aucune I/O
    // ---------------------------------------------------------------- //
    companion object {
        /** Page Settings → Apps → Accès à l'historique d'utilisation. */
        fun usageStatsSettingsIntent(): Intent =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /**
         * Demande "Ne pas optimiser la batterie pour Volt". Conforme aux
         * politiques Play (mais Volt est destiné F-Droid → pas de risque).
         */
        fun ignoreBatteryOptimizationsIntent(packageName: String): Intent =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Page Settings → Notifications de Volt (POST_NOTIFICATIONS). */
        fun notificationSettingsIntent(packageName: String): Intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Page F-Droid de Shizuku (installation). */
        fun shizukuFdroidIntent(): Intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://f-droid.org/packages/moe.shizuku.privileged.api/")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
