package com.tonnomdeved.volt.data.hibernation.whitelist

import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Détermine si une application est protégée contre l'hibernation.
 *
 * **5 couches stratifiées par confiance, évaluées dans l'ordre** :
 *  1. Override utilisateur PIN (court-circuit immédiat)
 *  2. App système (INVIOLABLE)
 *  3. Rôles critiques (INVIOLABLE pour DIALER/SMS/HOME)
 *  4. Services système actifs (IME, accessibilité, VPN, device admin, etc.)
 *  5. Heuristiques (autofill déclaré) + curated list
 *
 * Le flag `userForceHibernate` invalide les couches 3-5 *sauf* les
 * INVIOLABLE (couches 1, 2 et HoldsRole/DIALER|SMS|HOME). Les utilisateurs
 * doivent pouvoir signer sciemment l'hibernation d'une app marquée comme
 * STRONG/MODERATE — mais jamais d'une app dont l'absence casserait le device.
 *
 * **Performance** : toutes les lookups système se font sur [Dispatchers.IO].
 * Les caches `Set<String>` (IME, accessibility, autofill) sont recalculés
 * à chaque appel — c'est OK car cette résolution n'est appelée que lors
 * des décisions ponctuelles (UI ou sweep), pas dans une boucle chaude.
 */
class WhitelistResolver(private val context: Context) {

    /** Rôles dont la perte casse fondamentalement l'expérience device. */
    private val inviolableRoles: Set<String> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setOf(
            RoleManager.ROLE_DIALER,
            RoleManager.ROLE_SMS,
            RoleManager.ROLE_HOME
        ) else emptySet()
    }

    /** Rôles importants mais contournables avec userForceHibernate. */
    private val strongRoles: Set<String> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setOf(
            RoleManager.ROLE_BROWSER,
            RoleManager.ROLE_EMERGENCY,
            RoleManager.ROLE_CALL_REDIRECTION,
            RoleManager.ROLE_CALL_SCREENING
        ) else emptySet()
    }

    suspend fun isProtected(
        packageName: String,
        userPinned: Boolean = false,
        userForceHibernate: Boolean = false
    ): WhitelistReason? = withContext(Dispatchers.IO) {

        // === Couche 0 — override utilisateur explicite ============== //
        if (userPinned) return@withContext WhitelistReason.UserPinned

        // === Couche 1 — système (INVIOLABLE) ======================== //
        if (isSystemApp(packageName)) return@withContext WhitelistReason.SystemApp

        // === Couche 2 — rôles ======================================= //
        activeRole(packageName)?.let { role ->
            val isInviolable = role in inviolableRoles
            // Inviolable : on retourne tout de suite, override impossible.
            if (isInviolable) return@withContext WhitelistReason.HoldsRole(role)
            // Strong : on retourne sauf si userForceHibernate
            if (!userForceHibernate) return@withContext WhitelistReason.HoldsRole(role)
        }

        // Si userForceHibernate, on ignore les couches 3-5 (STRONG/MODERATE).
        if (userForceHibernate) return@withContext null

        // === Couche 3 — services système actifs ===================== //
        if (isActiveIme(packageName)) return@withContext WhitelistReason.ActiveInputMethod
        if (isActiveAccessibilityService(packageName))
            return@withContext WhitelistReason.ActiveAccessibilityService
        if (isLikelyActiveVpn(packageName)) return@withContext WhitelistReason.ActiveVpn
        if (isDeviceAdmin(packageName)) return@withContext WhitelistReason.DeviceAdmin
        if (isLiveWallpaper(packageName)) return@withContext WhitelistReason.LiveWallpaper
        if (isNotificationListener(packageName))
            return@withContext WhitelistReason.NotificationListener

        // === Couche 4 — heuristiques capability-declared ============ //
        declaredAutofillService(packageName)?.let {
            return@withContext WhitelistReason.DeclaresAutofill(it)
        }

        // === Couche 5 — curated list ================================ //
        if (packageName in CuratedWhitelist.PACKAGES)
            return@withContext WhitelistReason.CuratedSafetyList

        return@withContext null
    }

    // ============================================================== //
    // Détecteurs par couche
    // ============================================================== //

    private fun isSystemApp(packageName: String): Boolean {
        val info = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return false
        return (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
               (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    /**
     * Retourne le rôle détenu par [packageName], ou null.
     *
     * `RoleManager.getRoleHolders(String)` est passée `@SystemApi` / `@hide`
     * depuis Android 14+ — on l'invoque via réflexion. Cohérent avec la
     * cible power-user de Volt (grant ADB déjà accepté pour CHANGE_APP_IDLE_STATE).
     * Si la réflexion échoue (hidden API blacklist stricte), on retombe
     * silencieusement sur null — les autres couches whitelist couvrent le reste.
     */
    private fun activeRole(packageName: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rm = runCatching {
            context.getSystemService(RoleManager::class.java)
        }.getOrNull() ?: return null
        val method = runCatching {
            RoleManager::class.java.getMethod("getRoleHolders", String::class.java)
        }.getOrNull() ?: return null
        return (inviolableRoles + strongRoles).firstOrNull { role ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                (method.invoke(rm, role) as? List<String>).orEmpty().contains(packageName)
            }.getOrDefault(false)
        }
    }

    private fun isActiveIme(packageName: String): Boolean {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isActiveAccessibilityService(packageName: String): Boolean {
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    /**
     * Heuristique VPN : retourne true si **au moins un VPN est actif sur
     * le device** ET si [packageName] déclare un `VpnService` dans son
     * Manifest. Pas parfait (on ne sait pas *quel* VPN précisément est
     * actif sans permission privilégiée), mais ceinture-et-bretelles.
     */
    private fun isLikelyActiveVpn(packageName: String): Boolean {
        val anyVpnActive = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm?.allNetworks?.any { net ->
                cm.getNetworkCapabilities(net)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            } ?: false
        }.getOrDefault(false)
        if (!anyVpnActive) return false

        // L'app déclare-t-elle un VpnService ?
        val intent = Intent("android.net.VpnService")
        val resolved = runCatching {
            context.packageManager.queryIntentServices(intent, 0)
        }.getOrNull().orEmpty()
        return resolved.any { it.serviceInfo.packageName == packageName }
    }

    private fun isDeviceAdmin(packageName: String): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.activeAdmins?.any { it.packageName == packageName } == true
    }

    private fun isLiveWallpaper(packageName: String): Boolean {
        val wm = runCatching { WallpaperManager.getInstance(context) }.getOrNull() ?: return false
        return wm.wallpaperInfo?.packageName == packageName
    }

    private fun isNotificationListener(packageName: String): Boolean {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
        return packageName in enabled
    }

    private fun declaredAutofillService(packageName: String): String? {
        val intent = Intent("android.service.autofill.AutofillService")
        val resolved = runCatching {
            context.packageManager.queryIntentServices(intent, 0)
        }.getOrNull().orEmpty()
        return resolved.firstOrNull { it.serviceInfo.packageName == packageName }
            ?.serviceInfo?.name
    }
}
