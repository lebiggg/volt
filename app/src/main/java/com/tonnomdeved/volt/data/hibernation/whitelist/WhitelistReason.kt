package com.tonnomdeved.volt.data.hibernation.whitelist

/**
 * Raisons pour lesquelles une application peut être protégée contre
 * l'hibernation. Sealed pour permettre à la UI d'afficher un message
 * spécifique par raison.
 *
 * **Hiérarchie de confiance** (la plus forte d'abord) :
 *  1. [SystemApp] — inviolable
 *  2. [HoldsRole] — inviolable pour DIALER/SMS/HOME
 *  3. [ActiveInputMethod], [ActiveAccessibilityService], [ActiveVpn],
 *     [DeviceAdmin], [LiveWallpaper], [NotificationListener] — contournable
 *     par [com.tonnomdeved.volt.data.hibernation.HibernationPolicy.userForceHibernate]
 *  4. [DeclaresAutofill], [CuratedSafetyList] — contournable
 *  5. [UserPinned] — décision utilisateur explicite, contournable seulement
 *     en désépinglant manuellement
 *
 * **Note P1** : seul [SystemApp] est implémenté dans cette phase.
 * Les autres raisons sont déclarées pour fixer le contrat et seront
 * activées en P2 (Decision).
 */
sealed class WhitelistReason(val severity: Severity) {

    enum class Severity {
        /** Inviolable — aucun override ne peut hiberner cette app. */
        INVIOLABLE,
        /** Forte — override possible mais avec double confirmation. */
        STRONG,
        /** Modérée — override possible avec confirmation simple. */
        MODERATE
    }

    // ---------------------------------------------------------------- //
    // Couche 1 — système
    // ---------------------------------------------------------------- //
    data object SystemApp : WhitelistReason(Severity.INVIOLABLE)

    // ---------------------------------------------------------------- //
    // Couche 2 — rôles critiques (P2)
    // ---------------------------------------------------------------- //
    data class HoldsRole(val roleName: String) : WhitelistReason(Severity.INVIOLABLE)

    // ---------------------------------------------------------------- //
    // Couche 3 — services système actifs (P2)
    // ---------------------------------------------------------------- //
    data object ActiveInputMethod : WhitelistReason(Severity.STRONG)
    data object ActiveAccessibilityService : WhitelistReason(Severity.STRONG)
    data object ActiveVpn : WhitelistReason(Severity.STRONG)
    data object DeviceAdmin : WhitelistReason(Severity.STRONG)
    data object LiveWallpaper : WhitelistReason(Severity.MODERATE)
    data object NotificationListener : WhitelistReason(Severity.STRONG)

    // ---------------------------------------------------------------- //
    // Couche 4 — heuristiques capability-declared (P2)
    // ---------------------------------------------------------------- //
    data class DeclaresAutofill(val service: String) : WhitelistReason(Severity.STRONG)

    // ---------------------------------------------------------------- //
    // Couche 5 — liste curated (P2)
    // ---------------------------------------------------------------- //
    data object CuratedSafetyList : WhitelistReason(Severity.MODERATE)

    // ---------------------------------------------------------------- //
    // Override utilisateur — toujours évalué en premier
    // ---------------------------------------------------------------- //
    data object UserPinned : WhitelistReason(Severity.STRONG)
}
