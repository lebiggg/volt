package com.tonnomdeved.volt

import android.app.Application

/**
 * Application Volt.
 *
 * Référencée par le Manifest (`android:name=".VoltApplication"`) — c'est
 * elle qui instancie et expose le [VoltContainer] (service locator).
 *
 * **Invariants** :
 *  - `container` est créé au premier accès (initialisation paresseuse).
 *    `Application.onCreate()` reste rapide → temps de boot du process
 *    minimal.
 *  - L'API est `lateinit val` côté contrat public : impossible de la
 *    réassigner après instanciation, et lecture transparente.
 *  - Aucune init lourde au démarrage (pas de Room.build, pas de
 *    PackageManager scan). Tout reste à la demande.
 */
class VoltApplication : Application() {

    /**
     * Accès au service locator depuis n'importe quel composant Android :
     * ```
     * val controller = (applicationContext as VoltApplication).container.hibernationController
     * ```
     */
    val container: VoltContainer by lazy { VoltContainer(this) }
}
