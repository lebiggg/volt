package com.tonnomdeved.volt.data.forensics

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.util.Log
import com.tonnomdeved.volt.BuildConfig
import com.tonnomdeved.volt.data.hibernation.shizuku.ShizukuGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Analyseur Forensics — produit un [NightReport] répondant à « qu'est-ce qui
 * a réveillé / consommé pendant la nuit ? ».
 *
 * **Sources de données (toutes des observations système, zéro invention)** :
 *  1. `UsageStatsManager.queryEvents` → passages au premier plan par app.
 *  2. `NetworkStatsManager` → octets transmis en arrière-plan par UID.
 *  3. `dumpsys batterystats` via Shizuku → wakelocks/alarmes (best-effort).
 *
 * Si Shizuku est absent, la composante wakelock est `null` (la UI affiche
 * « ? »), mais les deux autres signaux restent disponibles.
 */
class ForensicsAnalyzer(
    private val context: Context,
    private val shizuku: ShizukuGateway
) {

    private val usageStatsManager: UsageStatsManager? by lazy {
        runCatching { context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager }
            .getOrNull()
    }
    private val networkStatsManager: NetworkStatsManager? by lazy {
        runCatching { context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager }
            .getOrNull()
    }

    /**
     * Analyse la fenêtre `[now - windowMs, now]`.
     *
     * @param windowMs durée de la fenêtre (défaut 8h ≈ une nuit de sommeil).
     */
    suspend fun analyze(
        windowMs: Long = DEFAULT_WINDOW_MS,
        now: Long = System.currentTimeMillis()
    ): NightReport = withContext(Dispatchers.Default) {
        val start = now - windowMs
        val pm = context.packageManager

        // 1) Passages premier plan par package
        val fgEvents = foregroundEventsByPackage(start, now)

        // 2) Wakelocks via dumpsys (best-effort)
        val wakeups = wakeupsByPackage()
        val wakeAvailable = wakeups != null

        // 3) Réseau background par package — pour les packages qui ont bougé
        //    (fg events ou wakeups) afin de limiter les lookups.
        val candidates = (fgEvents.keys + (wakeups?.keys ?: emptySet())).toMutableSet()
        // Ajouter aussi les gros consommateurs réseau même sans fg event :
        val netByPkg = backgroundBytesByPackage(start, now, candidates)

        val allPkgs = (candidates + netByPkg.keys)
            .filter { it != context.packageName }
            .toSet()

        val apps = allPkgs.mapNotNull { pkg ->
            val fg = fgEvents[pkg] ?: 0
            val bytes = netByPkg[pkg] ?: 0L
            val wk = wakeups?.get(pkg)
            // Ignorer les apps totalement inertes (aucun signal)
            if (fg == 0 && bytes == 0L && (wk == null || wk == 0)) return@mapNotNull null
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            AppNightActivity(
                packageName = pkg,
                label = label,
                foregroundEvents = fg,
                backgroundBytes = bytes,
                wakeups = wk,
                impact = computeImpact(fg, bytes, wk)
            )
        }.sortedByDescending { it.impact }

        NightReport(
            windowStartMs = start,
            windowEndMs = now,
            apps = apps,
            wakelockDataAvailable = wakeAvailable
        )
    }

    // ============================================================== //
    // Sources
    // ============================================================== //

    private fun foregroundEventsByPackage(start: Long, end: Long): Map<String, Int> {
        val usm = usageStatsManager ?: return emptyMap()
        val events = runCatching { usm.queryEvents(start, end) }.getOrNull() ?: return emptyMap()
        val counts = HashMap<String, Int>()
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val isWake = ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                         ev.eventType == UsageEvents.Event.FOREGROUND_SERVICE_START
            if (isWake) counts[ev.packageName] = (counts[ev.packageName] ?: 0) + 1
        }
        return counts
    }

    private fun backgroundBytesByPackage(
        start: Long,
        end: Long,
        packages: Set<String>
    ): Map<String, Long> {
        val nsm = networkStatsManager ?: return emptyMap()
        val pm = context.packageManager
        val result = HashMap<String, Long>()
        packages.forEach { pkg ->
            val uid = runCatching { pm.getApplicationInfo(pkg, 0).uid }.getOrNull() ?: return@forEach
            val bytes = runCatching { uidBytes(nsm, uid, start, end) }.getOrDefault(0L)
            if (bytes > 0) result[pkg] = bytes
        }
        return result
    }

    private fun uidBytes(nsm: NetworkStatsManager, uid: Int, start: Long, end: Long): Long {
        var total = 0L
        @Suppress("DEPRECATION")
        val stats = nsm.queryDetailsForUid(ConnectivityManager.TYPE_MOBILE, null, start, end, uid)
        val b = NetworkStats.Bucket()
        while (stats.hasNextBucket()) {
            stats.getNextBucket(b)
            if (b.state == NetworkStats.Bucket.STATE_DEFAULT) total += b.rxBytes + b.txBytes
        }
        stats.close()
        return total
    }

    /**
     * Parse `dumpsys batterystats` pour compter les wakeups par package.
     * Best-effort : retourne `null` si Shizuku indisponible.
     *
     * **Format réel observé sur Android 16 / GrapheneOS** : l'historique batterie
     * attribue chaque réveil radio/CPU à un UID via `wakeupap=<uid>:"<raison>"`,
     * où `<uid>` est soit un nombre système (ex. `1000`), soit `u0aNNN` (apps).
     * On compte les occurrences par UID et on résout l'UID → packageName.
     */
    private suspend fun wakeupsByPackage(): Map<String, Int>? {
        if (shizuku.checkAvailability() != ShizukuGateway.Availability.READY) return null
        val dump = shizuku.captureShell("dumpsys", "batterystats").getOrNull() ?: return null
        if (dump.isBlank()) return null

        val pm = context.packageManager
        val byUid = HashMap<Int, Int>()
        // Capture "wakeupap=u0a129:" ou "wakeupap=1000:" — un réveil attribué.
        val regex = Regex("""wakeupap=(u\d+a\d+|\d+):""")
        regex.findAll(dump).forEach { m ->
            val uid = parseUidToken(m.groupValues[1]) ?: return@forEach
            byUid[uid] = (byUid[uid] ?: 0) + 1
        }

        val result = HashMap<String, Int>()
        byUid.forEach { (uid, count) ->
            val pkgs = runCatching { pm.getPackagesForUid(uid) }.getOrNull()
            // On attribue le compte au premier package du UID (cas mono-package courant).
            pkgs?.firstOrNull()?.let { pkg ->
                if (pkg != context.packageName) result[pkg] = (result[pkg] ?: 0) + count
            }
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "wakeups parsed for ${result.size} packages")
        return result
    }

    companion object {
        private const val TAG = "VoltForensics"
        private val DEFAULT_WINDOW_MS = TimeUnit.HOURS.toMillis(8)

        /**
         * Convertit un token UID de dumpsys en UID numérique.
         *  - "1000"   → 1000 (système)
         *  - "u0a129" → 10129 (user 0, appId 10000+129)
         *  - "u10a5"  → 1010005 (user 10)
         */
        fun parseUidToken(token: String): Int? {
            token.toIntOrNull()?.let { return it }
            val m = Regex("""u(\d+)a(\d+)""").matchEntire(token) ?: return null
            val user = m.groupValues[1].toIntOrNull() ?: return null
            val appId = m.groupValues[2].toIntOrNull() ?: return null
            return user * 100_000 + 10_000 + appId
        }

        /**
         * Score d'impact 0-100 — pur, testable.
         *
         * Pondération :
         *  - wakeups : le plus pénalisant (réveillent le CPU/modem). 0-50 pts.
         *  - foregroundEvents nocturnes : 0-30 pts.
         *  - octets background : 0-20 pts.
         */
        fun computeImpact(foregroundEvents: Int, backgroundBytes: Long, wakeups: Int?): Int {
            val wakeScore = when {
                wakeups == null      -> 0
                wakeups > 100        -> 50
                wakeups > 40         -> 38
                wakeups > 15         -> 25
                wakeups > 5          -> 12
                wakeups > 0          -> 5
                else                 -> 0
            }
            val fgScore = when {
                foregroundEvents > 30 -> 30
                foregroundEvents > 15 -> 22
                foregroundEvents > 7  -> 14
                foregroundEvents > 2  -> 7
                foregroundEvents > 0  -> 3
                else                  -> 0
            }
            val mb = backgroundBytes.toDouble() / (1024.0 * 1024.0)
            val netScore = when {
                mb > 50  -> 20
                mb > 15  -> 14
                mb > 3   -> 8
                mb > 0.5 -> 4
                else     -> 0
            }
            return (wakeScore + fgScore + netScore).coerceIn(0, 100)
        }
    }
}
