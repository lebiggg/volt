package com.tonnomdeved.volt.data.hibernation.nocivity

/**
 * Décomposition transparente du score de nocivité 0-100.
 *
 * **Pourquoi exposer le détail** : la philosophie GrapheneOS "explain
 * everything" interdit les scores opaques. L'utilisateur doit pouvoir
 * cliquer sur "92" et comprendre exactement pourquoi.
 *
 * **Valeurs `null`** : indiquent que la donnée n'a pas pu être collectée
 * (permission `DUMP` manquante pour wakeups, ou `BatteryStatsManager`
 * indisponible). La UI affiche "?" plutôt que "0" pour ces composantes —
 * intégrité informationnelle > complétude apparente.
 */
data class NocivityBreakdown(
    val packageName: String,

    /** Total clampé [0,100]. */
    val total: Int,

    /** Composante 1/5 : 0-30 pts selon le nombre de jours d'inactivité. */
    val inactivity: Int,

    /** Composante 2/5 : 0-25 pts selon le ratio temps en background. */
    val backgroundRatio: Int,

    /** Composante 3/5 : 0-20 pts selon les wakeups/jour. `null` si permission `DUMP` manquante. */
    val wakeups: Int?,

    /** Composante 4/5 : 0-15 pts selon les bytes transmis en background. */
    val networkBackground: Int,

    /** Composante 5/5 : 0-10 pts selon `BatteryStatsManager`. `null` si non dispo. */
    val batteryImpact: Int?,

    // --- Métriques brutes pour affichage tooltip --- //
    val daysSinceLastUse: Int,
    val backgroundBytesLast7d: Long,
    val foregroundTimeLast7dMs: Long
) {
    /** Code couleur sémantique pour la UI. */
    val severity: Severity = when {
        total >= 85 -> Severity.CRITICAL
        total >= 60 -> Severity.HIGH
        total >= 30 -> Severity.MODERATE
        else        -> Severity.CALM
    }

    enum class Severity { CALM, MODERATE, HIGH, CRITICAL }
}
