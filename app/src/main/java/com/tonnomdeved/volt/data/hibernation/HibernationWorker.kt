package com.tonnomdeved.volt.data.hibernation

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tonnomdeved.volt.BuildConfig
import com.tonnomdeved.volt.VoltApplication
import java.util.concurrent.TimeUnit

/**
 * Worker périodique (6h) qui réapplique toutes les politiques d'hibernation.
 *
 * **Pourquoi WorkManager** :
 *  - L'OS gère intelligemment le batching sous Doze → pas de réveil
 *    intempestif du modem Tensor G3.
 *  - Survit aux reboots, aux mises à jour Volt, et au kill du FGS principal.
 *  - Idempotent par design : si le sweep est différé de 3h, on ne perd rien.
 *
 * **Pourquoi 6h plutôt que 1h ou 24h** :
 *  - 24h : trop long, des apps peuvent dériver hors RESTRICTED entre deux sweeps
 *    si le système promeut spontanément (ex. après un launch user).
 *  - 1h : surconsommation IPC + énergie. WorkManager batchera de toute façon.
 *  - 6h : équilibre — 4 sweeps/jour, alignable avec les phases de charge typiques.
 *
 * **Contraintes** : aucune (sauf "device idle" pour HARD si on voulait être
 * strict). On préfère que le sweep s'exécute même device unplugged, car la
 * mission est *justement* de préserver la batterie.
 *
 * Le caller du sweep (UI ou `BatteryCommandService`) configure le worker
 * via [schedule]. [cancel] le retire si l'utilisateur désactive l'auto-hib.
 */
class HibernationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val controller = (applicationContext as VoltApplication)
            .container.hibernationController

        return runCatching {
            val applied = controller.applyAllPolicies()
            if (BuildConfig.DEBUG) Log.d(TAG, "sweep applied=$applied policies")
            Result.success()
        }.getOrElse { e ->
            if (BuildConfig.DEBUG) Log.w(TAG, "sweep failed", e)
            // Retry avec backoff intégré WorkManager (~30s, doublant)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "VoltHibWorker"
        const val WORK_NAME = "volt_hibernation_sweep"
        private const val PERIOD_HOURS = 6L
        // WorkManager exige un minimum de flex de 5 min — fenêtre de tolérance.
        private const val FLEX_MINUTES = 30L

        /** Programme (ou met à jour) le sweep périodique. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setRequiresStorageNotLow(false)
                .build()

            val request = PeriodicWorkRequestBuilder<HibernationWorker>(
                repeatInterval = PERIOD_HOURS, repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = FLEX_MINUTES, flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** Désinscrit le worker — appelé si l'utilisateur désactive l'auto-hibernation. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }
}
