package com.tonnomdeved.volt.data.hibernation

/**
 * Politique d'hibernation pour une application.
 *
 * Immuable (data class) — toute modification crée une nouvelle instance.
 * Persistée via [com.tonnomdeved.volt.data.hibernation.persistence.HibernationEntity].
 *
 * **Distinction sémantique fondamentale** :
 *  - [level] = ce que Volt *applique* à l'app.
 *  - [userPinned] = "ne JAMAIS hiberner" (override utilisateur fort).
 *  - [userForceHibernate] = "hiberner MÊME SI la whitelist veut me protéger"
 *    (override utilisateur, bloqué pour les apps SYSTEM/ROLE_DIALER/SMS/HOME
 *     même avec ce flag — sécurité inviolable au niveau du [HibernationController]).
 *
 * `userPinned == true && userForceHibernate == true` est interdit par le
 * Controller (les deux drapeaux sont exclusifs).
 */
data class HibernationPolicy(
    val packageName: String,
    val level: HibernationLevel,
    val userPinned: Boolean = false,
    val userForceHibernate: Boolean = false,
    /** Timestamp epoch ms du dernier `setAppStandbyBucket` réussi. 0 si jamais appliqué. */
    val lastAppliedAt: Long = 0L,
    /** Timestamp epoch ms du dernier wake-on-push. null = jamais. */
    val lastWakeAt: Long? = null,
    /** Timestamp epoch ms de création de la politique. */
    val createdAt: Long = 0L
) {
    init {
        require(!(userPinned && userForceHibernate)) {
            "userPinned et userForceHibernate sont mutuellement exclusifs"
        }
        require(packageName.isNotBlank()) { "packageName ne peut pas être vide" }
    }
}
