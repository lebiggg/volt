package com.tonnomdeved.volt.data.forensics

/**
 * Rapport d'activité nocturne — répond à « qu'est-ce qui a réveillé mon
 * téléphone cette nuit / pendant les dernières heures ? ».
 *
 * Produit par [ForensicsAnalyzer] à la demande. Non persisté : recalculé à
 * chaque scan pour éviter toute dérive entre la BDD et la réalité système.
 */
data class NightReport(
    val windowStartMs: Long,
    val windowEndMs: Long,
    /** Apps triées par impact décroissant. */
    val apps: List<AppNightActivity>,
    /** True si la donnée wakelock (Shizuku dumpsys) était disponible. */
    val wakelockDataAvailable: Boolean
) {
    val windowHours: Int get() = ((windowEndMs - windowStartMs) / 3_600_000L).toInt().coerceAtLeast(1)
    val totalBackgroundBytes: Long get() = apps.sumOf { it.backgroundBytes }
    val totalWakeups: Int get() = apps.sumOf { it.wakeups ?: 0 }
}

/**
 * Activité d'une app sur la fenêtre analysée.
 *
 * Toutes les métriques sont des observations système (UsageStatsManager,
 * NetworkStatsManager, dumpsys batterystats), jamais des estimations
 * inventées. `wakeups` est `null` si Shizuku n'était pas disponible pour
 * lire `dumpsys` — la UI affiche alors « ? » plutôt que 0.
 */
data class AppNightActivity(
    val packageName: String,
    val label: String,
    /** Nombre de passages au premier plan pendant la fenêtre. */
    val foregroundEvents: Int,
    /** Octets transmis en arrière-plan (rx+tx) pendant la fenêtre. */
    val backgroundBytes: Long,
    /** Nombre de wakelocks/alarmes recensés via dumpsys. null = donnée indisponible. */
    val wakeups: Int?,
    /** Score d'impact 0-100 (tri). */
    val impact: Int
) {
    val severity: Severity = when {
        impact >= 70 -> Severity.HIGH
        impact >= 35 -> Severity.MODERATE
        else         -> Severity.LOW
    }

    enum class Severity { LOW, MODERATE, HIGH }
}
