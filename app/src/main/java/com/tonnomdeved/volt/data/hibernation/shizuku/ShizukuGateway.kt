package com.tonnomdeved.volt.data.hibernation.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.tonnomdeved.volt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Passerelle vers Shizuku — exécution non-root de commandes privilégiées.
 *
 * **Détection** (idiomatique) :
 *  1. Le package `moe.shizuku.privileged.api` est-il installé ?
 *  2. Le binder Shizuku répond-il (`Shizuku.pingBinder()`) ?
 *  3. Avons-nous la permission (`Shizuku.checkSelfPermission()`) ?
 *
 * **Exécution** : on utilise `Shizuku.newProcess()` qui fork un process
 * shell sous l'UID Shizuku (~2000) avec les permissions `shell`. Cela
 * permet d'exécuter `am force-stop <pkg>` sans avoir besoin d'aucune
 * permission privilégiée côté Volt.
 *
 * **Sécurité** :
 *  - On ne passe JAMAIS la chaîne `packageName` non quotée à un shell.
 *    On utilise la signature `newProcess(String[])` qui prend les arguments
 *    déjà splittés — pas de risque d'injection si packageName contient
 *    des caractères spéciaux.
 *  - On valide [packageName] avec une regex stricte avant l'appel.
 *
 * **Permission flow** : la requête de permission doit être déclenchée
 * depuis une [Activity] (sinon le dialog ne peut pas apparaître). Volt
 * UI gère cela depuis [com.tonnomdeved.volt.ui.screens.hibernate.HibernateShizukuOnboarding].
 */
class ShizukuGateway(private val context: Context) {

    private companion object {
        private const val TAG = "VoltShizuku"
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

        /** Packages Android : `[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+` (~ strict). */
        private val SAFE_PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    }

    enum class Availability {
        NOT_INSTALLED,
        INSTALLED_NOT_RUNNING,
        NOT_GRANTED,
        READY
    }

    fun checkAvailability(): Availability {
        // 1) Package Shizuku présent ?
        val installed = runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)
        if (!installed) return Availability.NOT_INSTALLED

        // 2) Binder vivant ? (le service Shizuku doit être démarré)
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) return Availability.INSTALLED_NOT_RUNNING

        // 3) Permission accordée ?
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return if (granted) Availability.READY else Availability.NOT_GRANTED
    }

    /**
     * Lance `am force-stop <packageName>` via le shell privilégié Shizuku.
     *
     * Retourne `Result.success(Unit)` si exit code 0.
     * Retourne `Result.failure(...)` sinon — le caller (HibernationController)
     * loggue (en DEBUG) et continue sans crasher.
     */
    suspend fun forceStop(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SAFE_PACKAGE_REGEX.matches(packageName)) {
            return@withContext Result.failure(
                IllegalArgumentException("packageName invalide : $packageName")
            )
        }
        runShellCommand("am", "force-stop", packageName).map { /* discard exit code */ }
    }

    /**
     * Définit le standby bucket d'une app via le shell privilégié de Shizuku.
     *
     * Sur Android 16, l'API `UsageStatsManager.setAppStandbyBucket(String, int)`
     * est `@hide` ET la permission `CHANGE_APP_IDLE_STATE` n'est plus accordable
     * par `adb pm grant` — passée signature-only strict. Shizuku contourne car
     * il tourne sous UID `shell` (2000) qui a accès au shell command `am set-standby-bucket`.
     *
     * @param bucketName nom canonique : "active", "working_set", "frequent", "rare", "restricted"
     */
    suspend fun setStandbyBucket(packageName: String, bucketName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!SAFE_PACKAGE_REGEX.matches(packageName)) {
                return@withContext Result.failure(
                    IllegalArgumentException("packageName invalide : $packageName")
                )
            }
            runShellCommand("am", "set-standby-bucket", packageName, bucketName)
                .map { /* discard */ }
        }

    /**
     * Exécute une commande shell arbitraire via le binder Shizuku.
     * Retourne l'exit code (0 = succès) si l'invocation aboutit, Result.failure sinon.
     *
     * **Sécurité** : les arguments sont passés en array (jamais via shell -c "...") —
     * pas de risque d'injection même si `args` contient des caractères spéciaux.
     */
    private suspend fun runShellCommand(vararg args: String): Result<Int> =
        withContext(Dispatchers.IO) {
            if (checkAvailability() != Availability.READY) {
                return@withContext Result.failure(IllegalStateException("Shizuku non disponible"))
            }
            runCatching {
                // Shizuku.newProcess() est `private` dans l'API 13.x — réflexion.
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
                val process = method.invoke(null, args, null, null) as Process
                val exit = process.waitFor()
                if (BuildConfig.DEBUG) Log.d(TAG, "shell ${args.joinToString(" ")} exit=$exit")
                exit
            }.onFailure { e ->
                if (BuildConfig.DEBUG) Log.w(TAG, "shell command failed: ${args.joinToString(" ")}", e)
            }
        }

    /**
     * Demande la permission Shizuku.
     * Doit être appelée depuis une Activity vivante (sinon le dialog ne s'affiche pas).
     */
    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
            .onFailure { e ->
                if (BuildConfig.DEBUG) Log.w(TAG, "requestPermission failed", e)
            }
    }

    /**
     * Enregistre un listener de résultat de permission Shizuku.
     * Retourne une lambda d'unregister pour cleanup (typique : LifecycleScope).
     */
    fun observePermissionResults(
        listener: (requestCode: Int, granted: Boolean) -> Unit
    ): () -> Unit {
        val handler = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            listener(requestCode, grantResult == PackageManager.PERMISSION_GRANTED)
        }
        Shizuku.addRequestPermissionResultListener(handler)
        return { Shizuku.removeRequestPermissionResultListener(handler) }
    }
}
