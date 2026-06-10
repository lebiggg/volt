package com.tonnomdeved.volt.system

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tonnomdeved.volt.VoltApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile « Hiberner maintenant ».
 *
 * Un tap = sweep immédiat de toutes les politiques d'hibernation actives
 * (équivalent du déclencheur écran-éteint, mais à la demande). Utile avant
 * de poser le téléphone pour la nuit ou de partir en déplacement.
 *
 * **Cycle de vie** : TileService est bindé par SystemUI le temps du tap.
 * Le scope est annulé en `onDestroy` ; le sweep étant court (< 1 s pour
 * ~20 politiques), la coroutine a largement le temps de finir. Si SystemUI
 * unbind avant la fin, le sweep périodique (HibernationWorker, 6 h) sert
 * de filet de sécurité — aucune perte fonctionnelle.
 */
class VoltTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        super.onStartListening()
        // Tuile toujours "disponible" : l'action est ponctuelle, pas un toggle.
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Hiberner"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val controller = (applicationContext as VoltApplication)
            .container.hibernationController

        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Hibernation…"
            updateTile()
        }

        scope.launch {
            val applied = controller.applyAllPolicies()
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                label = if (applied > 0) "$applied app(s) ✓" else "Hiberner"
                updateTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
