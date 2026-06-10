package com.tonnomdeved.volt.data.hibernation.nocivity

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import com.tonnomdeved.volt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Calcule le score de nocivité d'une application — 0 (calme) à 100 (très nocif).
 *
 * **Formule** :
 * ```
 * SCORE = Inactivity(0-30) + BackgroundRatio(0-25)
 *       + Wakeups(0-20) + NetworkBg(0-15) + BatteryImpact(0-10)
 * ```
 *
 * Wakeups et BatteryImpact requièrent des permissions premium :
 *  - `android.permission.DUMP` (development) pour wakeups via `dumpsys batterystats`
 *  - `android.permission.BATTERY_STATS` (signature) pour `BatteryStatsManager`
 *
 * Quand ces permissions manquent, la composante est `null` et exclue du score.
 * Le score effectif est donc clampé entre 0 et 75 sans DUMP/BATTERY_STATS,
 * ce qui reste largement suffisant pour les décisions d'hibernation.
 *
 * **Thread** : tout le travail est en `Dispatchers.IO` — les appels à
 * `UsageStatsManager.queryEvents` et `NetworkStatsManager.queryDetailsForUid`
 * sont des IPC binders qui ne doivent jamais toucher le main thread.
 */
class NocivityScorer(private val context: Context) {

    private companion object {
        private const val TAG = "VoltNocivity"
        private val WINDOW_30D_MS = TimeUnit.DAYS.toMillis(30)
        private val WINDOW_7D_MS  = TimeUnit.DAYS.toMillis(7)
        private val DAY_MS        = TimeUnit.DAYS.toMillis(1)
    }

    private val usageStatsManager: UsageStatsManager? by lazy {
        runCatching {
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        }.getOrNull()
    }

    private val networkStatsManager: NetworkStatsManager? by lazy {
        runCatching {
            context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
        }.getOrNull()
    }

    /**
     * Calcule le breakdown complet d'une application.
     *
     * Si certaines composantes échouent (permission, app introuvable),
     * elles sont mises à `null` mais le score est calculé sur les
     * composantes disponibles. **Jamais d'exception remontée à l'appelant**.
     */
    suspend fun scoreOf(packageName: String, now: Long = System.currentTimeMillis()):
            NocivityBreakdown = withContext(Dispatchers.IO) {

        val daysSinceLastUse = computeDaysSinceLastUse(packageName, now)
        val foregroundMs     = computeForegroundTimeLast7d(packageName, now)

        val inactivity       = scoreInactivity(daysSinceLastUse)
        val backgroundRatio  = scoreBackgroundRatio(foregroundMs)
        val wakeups: Int?    = null  // P2 : pas de DUMP grant supposé — composante désactivée
        val (bgBytes, networkBg) = scoreNetworkBackground(packageName, now)
        val batteryImpact: Int? = null  // P2 : BATTERY_STATS signature non grantable

        val components = listOfNotNull(inactivity, backgroundRatio, wakeups, networkBg, batteryImpact)
        val total = components.sum().coerceIn(0, 100)

        NocivityBreakdown(
            packageName = packageName,
            total = total,
            inactivity = inactivity,
            backgroundRatio = backgroundRatio,
            wakeups = wakeups,
            networkBackground = networkBg,
            batteryImpact = batteryImpact,
            daysSinceLastUse = daysSinceLastUse,
            backgroundBytesLast7d = bgBytes,
            foregroundTimeLast7dMs = foregroundMs
        )
    }

    // ============================================================== //
    // Composante 1 — Inactivity (0-30)
    // ============================================================== //
    private fun computeDaysSinceLastUse(packageName: String, now: Long): Int {
        val usm = usageStatsManager ?: return 30
        val events = runCatching {
            usm.queryEvents(now - WINDOW_30D_MS, now)
        }.getOrNull() ?: return 30

        var lastFg = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == packageName &&
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (event.timeStamp > lastFg) lastFg = event.timeStamp
            }
        }
        if (lastFg == 0L) return 30  // jamais utilisée dans la fenêtre observée
        return ((now - lastFg) / DAY_MS).toInt().coerceIn(0, 30)
    }

    private fun scoreInactivity(days: Int): Int = days  // linéaire 0-30

    // ============================================================== //
    // Composante 2 — Background ratio (0-25)
    // ============================================================== //
    private fun computeForegroundTimeLast7d(packageName: String, now: Long): Long {
        val usm = usageStatsManager ?: return 0L
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - WINDOW_7D_MS, now)
        }.getOrNull() ?: return 0L
        return stats.firstOrNull { it.packageName == packageName }
            ?.totalTimeInForeground ?: 0L
    }

    private fun scoreBackgroundRatio(foregroundMs: Long): Int {
        val fgRatio = foregroundMs.toFloat() / WINDOW_7D_MS
        val bgRatio = (1f - fgRatio).coerceIn(0f, 1f)
        return (bgRatio * 25).toInt()
    }

    // ============================================================== //
    // Composante 4 — Network background bytes (0-15)
    // ============================================================== //
    private fun scoreNetworkBackground(packageName: String, now: Long): Pair<Long, Int> {
        val nsm = networkStatsManager ?: return 0L to 0
        val uid = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        }.getOrNull() ?: return 0L to 0

        val bgBytes = runCatching {
            queryUidBackgroundBytes(nsm, uid, now - WINDOW_7D_MS, now)
        }.onFailure { e ->
            if (BuildConfig.DEBUG) Log.w(TAG, "queryDetailsForUid failed for uid=$uid", e)
        }.getOrDefault(0L)

        val score = when {
            bgBytes > 100L * 1024 * 1024 -> 15  // > 100 Mo
            bgBytes >  30L * 1024 * 1024 -> 10
            bgBytes >   5L * 1024 * 1024 -> 5
            else                         -> 0
        }
        return bgBytes to score
    }

    /**
     * Somme les bytes (rx+tx) consommés par [uid] en background sur
     * [start, end]. Background = `bucket.state == STATE_DEFAULT`.
     *
     * Sur certaines builds Android, `queryDetailsForUid` retourne
     * uniquement des buckets `STATE_ALL` — dans ce cas on retombe sur
     * 50 % du total comme proxy raisonnable (heuristique défensive).
     */
    private fun queryUidBackgroundBytes(
        nsm: NetworkStatsManager,
        uid: Int,
        start: Long,
        end: Long
    ): Long {
        var bgBytes = 0L
        var totalBytes = 0L
        var sawBackgroundBucket = false

        val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ConnectivityManager.TYPE_MOBILE  // remplacé par NetworkTemplate côté framework
        else
            ConnectivityManager.TYPE_MOBILE

        @Suppress("DEPRECATION")
        val stats = nsm.queryDetailsForUid(networkType, /* subscriberId */ null, start, end, uid)
        val bucket = NetworkStats.Bucket()
        while (stats.hasNextBucket()) {
            stats.getNextBucket(bucket)
            val bytes = bucket.rxBytes + bucket.txBytes
            totalBytes += bytes
            if (bucket.state == NetworkStats.Bucket.STATE_DEFAULT) {
                bgBytes += bytes
                sawBackgroundBucket = true
            }
        }
        stats.close()

        // Fallback : si aucun bucket STATE_DEFAULT vu, on prend 50 % du total
        return if (sawBackgroundBucket) bgBytes else (totalBytes / 2)
    }
}
