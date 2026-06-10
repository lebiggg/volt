package com.tonnomdeved.volt.data.hibernation

import com.tonnomdeved.volt.data.hibernation.whitelist.WhitelistReason

/**
 * Résultat d'une opération d'hibernation. Sealed pour forcer l'exhaustivité
 * à l'appel (la UI doit gérer chaque cas explicitement).
 *
 * **Convention de nommage** : tout ce qui est `Blocked` reflète un refus
 * *intentionnel* par le système (whitelist) — pas une erreur technique.
 * `Failed` reflète un échec d'I/O ou d'IPC inattendu (permission révoquée,
 * binder Shizuku mort, etc.). La distinction est importante pour la UX
 * (Blocked = explication à l'utilisateur, Failed = retry ou diagnostic).
 */
sealed class HibernationResult {

    /** Opération réussie — l'état persisté correspond au niveau demandé. */
    data object Success : HibernationResult()

    /** L'opération aurait été un no-op (idempotence respectée). */
    data object Unchanged : HibernationResult()

    /**
     * Refus par la whitelist. La raison précise est exposée pour que la UI
     * puisse afficher un message contextualisé ("protégée car keyboard actif"
     * vs "protégée car gestionnaire 2FA connu").
     */
    data class Blocked(val reason: WhitelistReason) : HibernationResult()

    /**
     * Le niveau demandé exige Shizuku, mais Shizuku n'est pas disponible.
     * L'appelant peut décider de retomber sur [HibernationLevel.SOFT] via
     * [HibernationLevel.fallbackWithoutShizuku] et réessayer.
     */
    data class ShizukuUnavailable(val requested: HibernationLevel) : HibernationResult()

    /**
     * Échec d'I/O ou IPC. Ne devrait jamais arriver en fonctionnement normal —
     * traiter comme un bug à investiguer.
     */
    data class Failed(val error: Throwable) : HibernationResult()
}
