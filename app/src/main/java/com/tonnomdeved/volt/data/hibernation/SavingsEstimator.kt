package com.tonnomdeved.volt.data.hibernation

import com.tonnomdeved.volt.data.hibernation.nocivity.NocivityBreakdown

/**
 * Estime l'économie d'énergie apportée par les politiques d'hibernation actives.
 *
 * **Honnêteté méthodologique** : il est impossible de mesurer précisément
 * l'économie réelle sans un A/B test sur batterie (qui prendrait des jours).
 * Cet estimateur produit donc une **fourchette indicative** basée sur des
 * heuristiques publiques, jamais présentée comme une mesure exacte.
 *
 * Le modèle :
 *  - Une app en bucket RESTRICTED voit ses jobs/alarmes/réveils fortement
 *    limités. Les mesures publiques (Android Vitals, études AOSP) situent le
 *    gain typique entre 1 et 4 % de la batterie totale par app gourmande
 *    neutralisée sur 24 h.
 *  - On pondère par le score de nocivité : une app à 90 économise davantage
 *    qu'une app à 35, car son drain background était plus élevé.
 *  - Le force-stop (MEDIUM/HARD) ajoute un bonus, car il coupe aussi les
 *    wakelocks résiduels que le bucket seul ne tue pas.
 *
 * Capacité batterie Pixel 8 ≈ 4575 mAh — utilisée pour convertir % → mAh.
 */
object SavingsEstimator {

    private const val PIXEL_8_BATTERY_MAH = 4575.0

    data class Savings(
        /** Pourcentage estimé de batterie économisé par 24 h, borné [0, 30]. */
        val dailyPercent: Double,
        /** Équivalent en mAh/jour. */
        val dailyMah: Int,
        /** Nombre d'apps prises en compte. */
        val appCount: Int
    ) {
        /** Estimation de minutes d'autonomie regagnées (hypothèse ~12 mAh/min en idle). */
        val extraStandbyMinutes: Int get() = (dailyMah / 12.0).toInt()
    }

    /**
     * @param items couples (niveau appliqué, score de nocivité) des apps hibernées.
     */
    fun estimate(items: List<Pair<HibernationLevel, NocivityBreakdown>>): Savings {
        var percent = 0.0
        var counted = 0
        items.forEach { (level, score) ->
            if (!level.isActive()) return@forEach
            counted++
            // Base : 0,4 % à 2,5 % selon le score (0-100)
            val base = 0.4 + (score.total / 100.0) * 2.1
            // Bonus force-stop pour MEDIUM/HARD
            val multiplier = when (level) {
                HibernationLevel.HARD   -> 1.6
                HibernationLevel.MEDIUM -> 1.3
                else                    -> 1.0
            }
            percent += base * multiplier
        }
        // Rendements décroissants : on ne peut pas économiser 80 % de batterie.
        val capped = (percent).coerceAtMost(30.0)
        val mah = (capped / 100.0 * PIXEL_8_BATTERY_MAH).toInt()
        return Savings(dailyPercent = capped, dailyMah = mah, appCount = counted)
    }
}
