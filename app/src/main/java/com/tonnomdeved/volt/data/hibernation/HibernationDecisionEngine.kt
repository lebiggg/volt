package com.tonnomdeved.volt.data.hibernation

import com.tonnomdeved.volt.data.hibernation.nocivity.NocivityBreakdown
import com.tonnomdeved.volt.data.hibernation.nocivity.NocivityScorer
import com.tonnomdeved.volt.data.hibernation.whitelist.WhitelistResolver

/**
 * Moteur de décision auto-hibernation — décide quel [HibernationLevel]
 * appliquer à une app en fonction de :
 *  - son score de nocivité (via [NocivityScorer])
 *  - sa présence éventuelle dans la whitelist (via [WhitelistResolver])
 *  - les seuils utilisateur configurables ([Thresholds])
 *
 * **Pur** : aucun side-effect. C'est le [HibernationController] qui
 * appliquera ensuite la décision. Cette séparation permet de :
 *  - tester l'algorithme sans toucher Android API
 *  - simuler "what if" dans la UI (« qu'arriverait-il si je passais ce
 *    seuil à 75 ? »)
 *  - changer la formule sans modifier le pipeline d'application
 */
class HibernationDecisionEngine(
    private val scorer: NocivityScorer,
    private val whitelist: WhitelistResolver
) {

    /**
     * Seuils par défaut — alignés sur la matrice de couleurs du
     * [NocivityBreakdown.Severity].
     */
    data class Thresholds(
        val softAbove: Int = 30,
        val mediumAbove: Int = 60,
        val hardAbove: Int = 85
    ) {
        init {
            require(softAbove in 0..100) { "softAbove hors bornes" }
            require(mediumAbove in softAbove..100) { "mediumAbove < softAbove" }
            require(hardAbove in mediumAbove..100) { "hardAbove < mediumAbove" }
        }

        companion object { val DEFAULT = Thresholds() }
    }

    /** Décision complète incluant la justification — utilisée par la UI. */
    data class Decision(
        val packageName: String,
        val level: HibernationLevel,
        val score: NocivityBreakdown,
        val rationale: Rationale
    )

    /** Raison textuelle pour l'affichage en UI. */
    sealed class Rationale {
        data object Protected : Rationale()
        data object BelowSoftThreshold : Rationale()
        data class CrossesSoftThreshold(val score: Int) : Rationale()
        data class CrossesMediumThreshold(val score: Int) : Rationale()
        data class CrossesHardThreshold(val score: Int) : Rationale()
    }

    suspend fun decide(
        packageName: String,
        thresholds: Thresholds = Thresholds.DEFAULT,
        userPinned: Boolean = false,
        userForceHibernate: Boolean = false
    ): Decision {
        val score = scorer.scoreOf(packageName)

        // Whitelist d'abord — court-circuit immédiat
        val protection = whitelist.isProtected(packageName, userPinned, userForceHibernate)
        if (protection != null) {
            return Decision(packageName, HibernationLevel.OFF, score, Rationale.Protected)
        }

        // Décision basée sur le score
        val (level, rationale) = when {
            score.total >= thresholds.hardAbove ->
                HibernationLevel.HARD to Rationale.CrossesHardThreshold(score.total)
            score.total >= thresholds.mediumAbove ->
                HibernationLevel.MEDIUM to Rationale.CrossesMediumThreshold(score.total)
            score.total >= thresholds.softAbove ->
                HibernationLevel.SOFT to Rationale.CrossesSoftThreshold(score.total)
            else ->
                HibernationLevel.OFF to Rationale.BelowSoftThreshold
        }
        return Decision(packageName, level, score, rationale)
    }
}
