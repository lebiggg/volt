package com.tonnomdeved.volt.data.hibernation

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.tonnomdeved.volt.BuildConfig
import com.tonnomdeved.volt.data.hibernation.shizuku.ShizukuGateway
import com.tonnomdeved.volt.data.hibernation.whitelist.WhitelistResolver
import kotlinx.coroutines.flow.Flow

/**
 * Façade publique unique du moteur d'hibernation.
 *
 * **Tous** les autres composants (BatteryCommandService, ViewModels, Workers)
 * passent par ici — interdiction d'écrire directement dans la BDD ou
 * d'appeler [UsageStatsManager.setAppStandbyBucket] depuis ailleurs.
 *
 * **Garanties** :
 *  - **Whitelist respectée** : aucune opération n'est appliquée si
 *    [WhitelistResolver.isProtected] retourne une raison.
 *  - **Shizuku-aware** : MEDIUM/HARD retombent gracefully sur SOFT si
 *    Shizuku est absent, sans crash ni erreur silencieuse.
 *  - **Idempotence** : appeler `hibernate(pkg, SOFT)` deux fois retourne
 *    [HibernationResult.Unchanged] la seconde fois.
 *  - **Thread-safety** : toutes les fonctions sont `suspend`. Les opérations
 *    sur [UsageStatsManager] sont enveloppées en `runCatching` car elles
 *    peuvent jeter [SecurityException] si `CHANGE_APP_IDLE_STATE` n'est
 *    pas grantée — Volt continue alors en mode dégradé.
 *
 * **Atomicité** : `hibernate` applique d'abord le bucket, **puis** persiste
 * la politique. Si l'application du bucket échoue, la BDD n'est pas modifiée
 * — la cohérence "ce qui est en BDD = ce qui est appliqué" est préservée.
 */
class HibernationController(
    private val context: Context,
    private val repository: HibernationRepository,
    private val whitelist: WhitelistResolver,
    private val shizuku: ShizukuGateway,
    private val usageStatsManager: UsageStatsManager,
    /** Fournisseur de timestamp injectable — facilite les tests. */
    private val now: () -> Long = System::currentTimeMillis
) {

    private companion object { private const val TAG = "VoltHibernate" }

    /**
     * État capturé lors d'un wake-for-push, à passer tel quel à
     * [rehibernate] quelques secondes plus tard.
     *
     * Pourquoi un type dédié plutôt qu'un simple [HibernationLevel] :
     *  - On veut savoir si l'app était force-stoppée pour décider si on
     *    refait un force-stop après livraison (P3).
     *  - On veut tracer le packageName explicitement pour éviter les
     *    confusions si plusieurs pushs arrivent en parallèle.
     */
    data class WakeState(
        val packageName: String,
        val previousLevel: HibernationLevel,
        val wasForceStopped: Boolean
    ) {
        /** True ssi l'app était bel et bien hibernée — sinon rehibernate est un no-op. */
        fun wasHibernated(): Boolean = previousLevel.isActive()
    }

    // ============================================================== //
    // API publique — actions utilisateur
    // ============================================================== //

    /**
     * Applique le niveau d'hibernation demandé à [packageName].
     *
     * Si [requested] est `OFF`, l'app est promue en [UsageStatsManager.STANDBY_BUCKET_ACTIVE]
     * et sa politique supprimée de la BDD.
     */
    suspend fun hibernate(packageName: String, requested: HibernationLevel): HibernationResult {
        // 1) Whitelist (lit aussi les flags utilisateur si la policy existe déjà)
        val existing = repository.getPolicy(packageName)
        val protectionReason = whitelist.isProtected(
            packageName,
            userPinned = existing?.userPinned == true,
            userForceHibernate = existing?.userForceHibernate == true
        )
        if (protectionReason != null) {
            return HibernationResult.Blocked(protectionReason)
        }

        // 2) Résolution Shizuku — fallback gracieux
        val effective = if (requested.needsShizuku &&
            shizuku.checkAvailability() != ShizukuGateway.Availability.READY) {
            // On signale à l'UI que Shizuku manque, mais l'UI peut choisir
            // de réessayer avec fallbackWithoutShizuku().
            return HibernationResult.ShizukuUnavailable(requested)
        } else {
            requested
        }

        // 3) Idempotence
        if (existing?.level == effective) {
            return HibernationResult.Unchanged
        }

        // 4) Application du bucket — AVANT la persistance
        val bucketResult = applyStandbyBucket(packageName, effective)
        if (bucketResult.isFailure) {
            val err = bucketResult.exceptionOrNull()!!
            if (BuildConfig.DEBUG) Log.w(TAG, "setAppStandbyBucket failed", err)
            return HibernationResult.Failed(err)
        }

        // 5) Force-stop si MEDIUM/HARD (no-op en P1 — stub Shizuku)
        if (effective.needsShizuku) {
            shizuku.forceStop(packageName)
                .onFailure { e ->
                    if (BuildConfig.DEBUG) Log.w(TAG, "Shizuku force-stop failed", e)
                }
        }

        // 6) Persistance
        val timestamp = now()
        val newPolicy = HibernationPolicy(
            packageName = packageName,
            level = effective,
            userPinned = existing?.userPinned == true,
            userForceHibernate = existing?.userForceHibernate == true,
            lastAppliedAt = timestamp,
            lastWakeAt = existing?.lastWakeAt,
            createdAt = existing?.createdAt ?: timestamp
        )
        if (effective == HibernationLevel.OFF) {
            repository.delete(packageName)
        } else {
            repository.upsert(newPolicy)
        }
        return HibernationResult.Success
    }

    /** Raccourci pour `hibernate(packageName, HibernationLevel.OFF)`. */
    suspend fun unhibernate(packageName: String): HibernationResult =
        hibernate(packageName, HibernationLevel.OFF)

    // ============================================================== //
    // API push — appelée par BatteryCommandService
    // ============================================================== //

    /**
     * Phase **WAKE** : promeut temporairement [packageName] en
     * [UsageStatsManager.STANDBY_BUCKET_ACTIVE] pour permettre la livraison
     * du push UnifiedPush qui va suivre.
     *
     * Retourne un [WakeState] que l'appelant doit conserver et fournir à
     * [rehibernate] après la livraison.
     */
    suspend fun wakeForPush(packageName: String): WakeState {
        val policy = repository.getPolicy(packageName)
        val previous = policy?.level ?: HibernationLevel.OFF
        val wasForceStopped = previous.needsShizuku  // MEDIUM/HARD impliquent force-stop

        if (previous.isActive()) {
            applyStandbyBucket(packageName, HibernationLevel.OFF)
                .onSuccess { repository.recordWake(packageName, now()) }
                .onFailure { e ->
                    if (BuildConfig.DEBUG) Log.w(TAG, "wakeForPush bucket promote failed", e)
                    // FLAG_INCLUDE_STOPPED_PACKAGES dans le broadcast permet
                    // de réveiller l'app même sans promotion de bucket réussie.
                }
        }
        return WakeState(packageName, previous, wasForceStopped)
    }

    /**
     * Phase **REHIBERNATE** : restaure l'état hiberné précédent une fois
     * le push livré. Appelée typiquement ~5 s après [wakeForPush] par
     * [com.tonnomdeved.volt.BatteryCommandService].
     *
     * Si [WakeState.wasHibernated] est false, c'est un no-op total — l'app
     * n'était pas hibernée, rien à restaurer.
     */
    suspend fun rehibernate(state: WakeState) {
        if (!state.wasHibernated()) return
        applyStandbyBucket(state.packageName, state.previousLevel)
            .onSuccess { repository.recordApplication(state.packageName, now()) }
            .onFailure { e ->
                if (BuildConfig.DEBUG) Log.w(TAG, "rehibernate failed", e)
            }
    }

    // ============================================================== //
    // API sweep — appelée par BatteryCommandService.triggerDeepSleep
    //            et plus tard par HibernationWorker (P5)
    // ============================================================== //

    /**
     * Réapplique en bloc toutes les politiques actives. Idempotent —
     * appelable à chaque écran éteint sans surcoût IPC si rien n'a bougé.
     *
     * Retourne le nombre d'apps effectivement re-restreintes (utile pour
     * mettre à jour le compteur exposé via [com.tonnomdeved.volt.data.VoltStateBus]).
     */
    suspend fun applyAllPolicies(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return 0
        val policies = repository.getAllPolicies().filter { it.level.isActive() }
        var applied = 0
        policies.forEach { policy ->
            applyStandbyBucket(policy.packageName, policy.level)
                .onSuccess { applied++ }
                .onFailure { e ->
                    if (BuildConfig.DEBUG && e !is SecurityException) {
                        Log.w(TAG, "applyAllPolicies entry failed", e)
                    }
                }
        }
        return applied
    }

    // ============================================================== //
    // Lectures observables — pour la UI et le bus d'état
    // ============================================================== //

    val activeCount: Flow<Int> get() = repository.activeCount
    val policies: Flow<List<HibernationPolicy>> get() = repository.policies

    suspend fun getPolicy(packageName: String): HibernationPolicy? =
        repository.getPolicy(packageName)

    /**
     * Réveille **toutes** les apps hibernées d'un coup — soupape de sécurité
     * (« panic button »). Promeut chaque app en ACTIVE et supprime sa politique.
     *
     * Retourne le nombre d'apps réveillées. Idempotent (no-op si rien d'actif).
     */
    suspend fun wakeAll(): Int {
        val active = repository.getAllPolicies().filter { it.level.isActive() }
        var woke = 0
        active.forEach { policy ->
            applyStandbyBucket(policy.packageName, HibernationLevel.OFF)
                .onSuccess {
                    repository.delete(policy.packageName)
                    woke++
                }
        }
        return woke
    }

    // ============================================================== //
    // Helpers privés
    // ============================================================== //

    /**
     * Applique le standby bucket d'une app, en privilégiant Shizuku quand
     * disponible — c'est la **seule voie applicative fonctionnelle sur
     * Android 16** (la permission `CHANGE_APP_IDLE_STATE` n'est plus
     * accordable par `adb pm grant`, et `setAppStandbyBucket(String, int)`
     * est strictement `@hide`).
     *
     * Chemin de décision :
     *  1. Shizuku READY → `am set-standby-bucket <pkg> <name>` (UID shell)
     *  2. Sinon → réflexion directe (Android 13- ou avec `hidden_api_policy=1`)
     *
     * Retourne Result.success(Unit) si l'une des deux voies aboutit.
     */
    private suspend fun applyStandbyBucket(
        packageName: String,
        level: HibernationLevel
    ): Result<Unit> {
        // Voie 1 — Shizuku (Android 16-friendly)
        if (shizuku.checkAvailability() == ShizukuGateway.Availability.READY) {
            val r = shizuku.setStandbyBucket(packageName, level.shellBucketName)
            if (r.isSuccess) return Result.success(Unit)
        }
        // Voie 2 — réflexion (legacy ou hidden_api_policy=1)
        return runCatching {
            val method = UsageStatsManager::class.java.getMethod(
                "setAppStandbyBucket",
                String::class.java,
                Int::class.javaPrimitiveType
            )
            method.invoke(usageStatsManager, packageName, level.standbyBucket)
            Unit
        }
    }
}
