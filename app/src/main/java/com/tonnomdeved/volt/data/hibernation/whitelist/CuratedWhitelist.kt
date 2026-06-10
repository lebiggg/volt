package com.tonnomdeved.volt.data.hibernation.whitelist

/**
 * Liste curated de packages à ne **jamais** hiberner par défaut.
 *
 * **Critères d'inclusion** stricts — toute PR communautaire doit fournir
 * une justification écrite :
 *  1. App de 2FA / TOTP (perdre une notification = perdre un code)
 *  2. Gestionnaire de mots de passe (autofill cassé si hibernée)
 *  3. Messagerie chiffrée à fort enjeu (Signal, SimpleX, Briar…)
 *  4. App critique de l'écosystème GrapheneOS (Auditor, etc.)
 *  5. Bancaire / paiement à authentification temps réel
 *
 * **Hors de cette liste** : les apps grand public qu'on peut hiberner
 * sans risque (réseaux sociaux, jeux, météo, etc.). Si un utilisateur
 * regrette qu'une app soit hibernée, il peut la *pin* manuellement —
 * ce n'est pas le job de la curated list de protéger les goûts personnels.
 *
 * Maintenue ici comme `Set<String>` immutable bundlé dans l'APK :
 * aucune requête réseau au runtime, aucune surface d'attaque.
 */
internal object CuratedWhitelist {

    val PACKAGES: Set<String> = setOf(

        // ──────────────────────────────────────────────────────────── //
        // 2FA / TOTP / Hardware token managers
        // ──────────────────────────────────────────────────────────── //
        "com.beemdevelopment.aegis",                // Aegis Authenticator
        "org.shadowice.flocke.andotp",              // andOTP
        "me.tylerbwong.android.totp",
        "im.cyou.tofu",                             // Tofu Authenticator
        "com.bitwarden.authenticator",              // Bitwarden Authenticator
        "com.azure.authenticator",                  // Microsoft Authenticator
        "com.google.android.apps.authenticator2",   // Google Authenticator
        "com.duosecurity.duomobile",                // Duo Mobile
        "io.raivo.ios",                             // Raivo (port Android)
        "com.yubico.yubioath",                      // Yubico Authenticator
        "com.nordpass.android.authenticator",

        // ──────────────────────────────────────────────────────────── //
        // Password managers (autofill backend)
        // ──────────────────────────────────────────────────────────── //
        "com.x8bit.bitwarden",                      // Bitwarden
        "com.kunzisoft.keepass.libre",              // KeePassDX
        "com.kunzisoft.keepass.free",
        "org.sufficientlysecure.keepassx",          // KeePassDroid
        "com.machiav3lli.fdroid.keepassdroid",
        "io.proton.pass",                           // Proton Pass
        "com.mauriciotogneri.keepasstouch",
        "com.dashlane",                             // Dashlane
        "com.lastpass.lpandroid",                   // LastPass
        "com.onepassword.android",                  // 1Password 8

        // ──────────────────────────────────────────────────────────── //
        // Messageries chiffrées à fort enjeu
        // ──────────────────────────────────────────────────────────── //
        "org.thoughtcrime.securesms",               // Signal
        "im.molly.app",                             // Molly (fork Signal)
        "chat.simplex.app",                         // SimpleX Chat
        "org.briarproject.briar.android",           // Briar
        "im.conversations.android",                 // Conversations (XMPP)
        "eu.siacs.conversations",                   // Conversations alt build
        "im.vector.app",                            // Element (Matrix)
        "io.element.android",                       // Element X
        "org.deltachat",                            // Delta Chat
        "org.deltachat.tester",
        "messenger.session",                        // Session
        "network.loki.messenger",                   // Session legacy
        "im.threema.app.work",                      // Threema Work
        "ch.threema.app",                           // Threema
        "im.threema.app.libre",
        "org.tox.antox",                            // Antox (Tox)
        "org.thoughtcrime.redphone",
        "im.tox.antidote",

        // ──────────────────────────────────────────────────────────── //
        // GrapheneOS — apps système critiques (extra sécurité)
        // ──────────────────────────────────────────────────────────── //
        "app.grapheneos.auditor",                   // Attestation hardware
        "app.grapheneos.camera",                    // Camera GOS
        "app.grapheneos.pdfviewer",                 // PdfViewer GOS
        "app.attestation.auditor",                  // Auditor amont
        "org.bromite.bromite",                      // Vanadium variants
        "app.vanadium.webview",

        // ──────────────────────────────────────────────────────────── //
        // VPN clients (critiques pour la routine privacy)
        // ──────────────────────────────────────────────────────────── //
        "net.mullvad.mullvadvpn",                   // Mullvad
        "ch.protonvpn.android",                     // ProtonVPN
        "com.protonvpn.android",
        "org.calyxinstitute.vpn",                   // CalyxVPN
        "com.wireguard.android",                    // WireGuard
        "de.blinkt.openvpn",                        // OpenVPN for Android
        "org.torproject.android",                   // Orbot
        "org.briarproject.bramble",
        "com.invisv.mobile",                        // Invisv
        "com.ivpn.client",                          // IVPN

        // ──────────────────────────────────────────────────────────── //
        // Notes / vault chiffrés (perte sync = perte de données)
        // ──────────────────────────────────────────────────────────── //
        "org.joinmastodon.android",                 // pas critique mais cite-friendly
        "com.standardnotes",                        // Standard Notes
        "com.cryptomator",
        "org.cryptomator",
        "info.guardianproject.notepadbot",
    )
}
