package com.tonnomdeved.volt.data.hibernation

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.tonnomdeved.volt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exécute une passe d'auto-hibernation : pour chaque app installée lançable,
 * le [HibernationDecisionEngine] décide d'un niveau en fonction du score de
 * nocivité et des seuils configurés, puis le [HibernationController] l'applique.
 *
 * **Respect des choix manuels** : une app dont la politique a été fixée
 * manuellement par l'utilisateur (épinglée, ou niveau choisi à la main) n'est
 * jamais écrasée par l'auto-pass. On ne touche qu'aux apps sans politique,
 * ou aux apps dont la politique vient elle-même de l'auto (héritage propre).
 *
 * **Idempotence** : si une app est déjà au niveau décidé, c'est un no-op
 * (le controller retourne Unchanged).
 *
 * C'est le chaînon qui rend Volt réellement « automatique » — sans lui, le
 * DecisionEngine n'était jamais invoqué.
 */
class AutoHibernationRunner(
    private val context: Context,
    private val decisionEngine: HibernationDecisionEngine,
    private val controller: HibernationController
) {

    private companion object { private const val TAG = "VoltAutoHib" }

    /**
     * Lance une passe complète.
     *
     * @return nombre d'apps dont le niveau a changé.
     */
    suspend fun run(thresholds: HibernationDecisionEngine.Thresholds): Int =
        withContext(Dispatchers.Default) {
            val pm = context.packageManager
            val launchable = installedLaunchableApps(pm)
            var changed = 0

            launchable.forEach { pkg ->
                val existing = controller.getPolicy(pkg)
                // Ne pas écraser un choix manuel explicite (pin ou force-hibernate).
                if (existing?.userPinned == true || existing?.userForceHibernate == true) {
                    return@forEach
                }
                val decision = decisionEngine.decide(pkg, thresholds)
                // Si déjà au bon niveau, hibernate() renverra Unchanged (no-op).
                val result = controller.hibernate(pkg, decision.level)
                if (result is HibernationResult.Success) changed++
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "auto-hibernation pass: $changed changed")
            changed
        }

    private fun installedLaunchableApps(pm: PackageManager): List<String> =
        runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { info: ApplicationInfo ->
                    pm.getLaunchIntentForPackage(info.packageName) != null &&
                    info.packageName != context.packageName
                }
                .map { it.packageName }
                .toList()
        }.getOrDefault(emptyList())
}
