package com.tonnomdeved.volt.data.hibernation

import android.app.usage.UsageStatsManager

/**
 * Niveaux d'hibernation supportés par Volt.
 *
 * **Invariants importants** :
 *  - SOFT, MEDIUM et HARD partagent tous le même bucket cible
 *    ([UsageStatsManager.STANDBY_BUCKET_RESTRICTED]). La différence se joue
 *    sur la couche force-stop (Shizuku), pas sur le bucket.
 *  - OFF correspond explicitement à [UsageStatsManager.STANDBY_BUCKET_ACTIVE]
 *    pour garantir que désactiver l'hibernation rend immédiatement l'app
 *    pleinement opérationnelle (et non simplement "lente à promouvoir").
 *  - [needsShizuku] permet au [HibernationController] de retomber gracefully
 *    sur SOFT quand Shizuku n'est pas disponible — pas de crash, pas d'erreur
 *    silencieuse côté utilisateur.
 *  - [periodicForceStop] (P3) indique si un sweep périodique doit re-killer
 *    les processes spontanément réveillés. Préparé dès la P1 pour ne pas
 *    avoir à modifier l'enum plus tard.
 */
enum class HibernationLevel(
    val standbyBucket: Int,
    val needsShizuku: Boolean,
    val periodicForceStop: Boolean
) {
    /** Hibernation désactivée — l'app fonctionne normalement. */
    OFF(
        standbyBucket = UsageStatsManager.STANDBY_BUCKET_ACTIVE,
        needsShizuku = false,
        periodicForceStop = false
    ),

    /** Restriction de bucket uniquement. Aucun force-stop. */
    SOFT(
        standbyBucket = UsageStatsManager.STANDBY_BUCKET_RESTRICTED,
        needsShizuku = false,
        periodicForceStop = false
    ),

    /** SOFT + force-stop unique au déclenchement (écran éteint). */
    MEDIUM(
        standbyBucket = UsageStatsManager.STANDBY_BUCKET_RESTRICTED,
        needsShizuku = true,
        periodicForceStop = false
    ),

    /** SOFT + force-stop périodique toutes les ~2 h (sweep agressif). */
    HARD(
        standbyBucket = UsageStatsManager.STANDBY_BUCKET_RESTRICTED,
        needsShizuku = true,
        periodicForceStop = true
    );

    /**
     * Niveau effectif quand Shizuku est absent / non grant.
     * Permet au [HibernationController] de répondre cohéremment sans
     * jamais retourner d'erreur "non supporté" à l'utilisateur.
     */
    fun fallbackWithoutShizuku(): HibernationLevel =
        if (needsShizuku) SOFT else this

    /** True ssi le niveau implique une hibernation active. */
    fun isActive(): Boolean = this != OFF

    /**
     * Nom canonique du bucket pour `am set-standby-bucket <pkg> <name>`.
     * Sur Android 16, c'est la seule voie applicative possible — la voie
     * réflexion `setAppStandbyBucket(String, int)` est verrouillée.
     */
    val shellBucketName: String
        get() = when (standbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE      -> "active"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT    -> "frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE        -> "rare"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED  -> "restricted"
            else -> "active"
        }

    companion object {
        /** Décodage sûr depuis Room (String.name). Inconnu → OFF (safe default). */
        fun fromName(name: String?): HibernationLevel =
            entries.firstOrNull { it.name == name } ?: OFF
    }
}
