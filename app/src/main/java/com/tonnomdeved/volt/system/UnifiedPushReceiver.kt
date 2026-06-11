package com.tonnomdeved.volt.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tonnomdeved.volt.BuildConfig
import com.tonnomdeved.volt.VoltApplication
import com.tonnomdeved.volt.data.VoltPreferences
import com.tonnomdeved.volt.data.push.UnifiedPushProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receiver du protocole UnifiedPush, côté **distributeur**.
 *
 * Gère le handshake avec les apps connecteurs :
 *  - `REGISTER`   → mémorise token ↔ app, renvoie `NEW_ENDPOINT` (ou
 *                   `REGISTRATION_FAILED` si l'URL serveur n'est pas configurée).
 *  - `UNREGISTER` → supprime l'enregistrement, renvoie `UNREGISTERED`.
 *
 * **Identification sécurisée de l'app** : on privilégie le `creatorPackage`
 * du PendingIntent fourni dans `EXTRA_PI` (impossible à usurper), avec repli
 * sur `EXTRA_APPLICATION` puis le package résolu de l'intent.
 *
 * Déclaré `exported="true"` dans le Manifest (obligatoire pour recevoir les
 * broadcasts des connecteurs), avec les intent-filters UnifiedPush.
 */
class UnifiedPushReceiver : BroadcastReceiver() {

    private companion object { private const val TAG = "VoltUnifiedPush" }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UnifiedPushProtocol.ACTION_REGISTER   -> handleRegister(context, intent)
            UnifiedPushProtocol.ACTION_UNREGISTER -> handleUnregister(context, intent)
        }
    }

    private fun handleRegister(context: Context, intent: Intent) {
        val token = intent.getStringExtra(UnifiedPushProtocol.EXTRA_TOKEN) ?: return
        val appPackage = resolveConnectorPackage(intent) ?: run {
            if (BuildConfig.DEBUG) Log.w(TAG, "REGISTER sans package identifiable")
            return
        }

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val serverUrl = VoltPreferences(appContext).pushServerUrl.first()
                val endpoint = UnifiedPushProtocol.endpointFor(serverUrl, token)
                if (endpoint == null) {
                    // Pas d'URL serveur configurée → on ne peut pas fournir d'endpoint.
                    sendRegistrationFailed(appContext, appPackage, token)
                    return@launch
                }
                val container = (appContext as VoltApplication).container
                container.pushRegistrationRepository.register(
                    token = token,
                    packageName = appPackage,
                    endpoint = endpoint,
                    now = System.currentTimeMillis()
                )
                sendNewEndpoint(appContext, appPackage, token, endpoint)
                if (BuildConfig.DEBUG) Log.d(TAG, "registered $appPackage")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "register failed", e)
                sendRegistrationFailed(appContext, appPackage, token)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleUnregister(context: Context, intent: Intent) {
        val token = intent.getStringExtra(UnifiedPushProtocol.EXTRA_TOKEN) ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val container = (appContext as VoltApplication).container
                val reg = container.pushRegistrationRepository
                val pkg = container.pushRegistrationRepository.packageForToken(token)
                reg.unregisterToken(token)
                if (pkg != null) sendUnregistered(appContext, pkg, token)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "unregister failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    // ============================================================== //
    // Identification de l'app connecteur
    // ============================================================== //
    private fun resolveConnectorPackage(intent: Intent): String? {
        // 1) PendingIntent.creatorPackage — non usurpable.
        val pi = intent.getParcelableExtra(UnifiedPushProtocol.EXTRA_PI, PendingIntent::class.java)
        pi?.creatorPackage?.let { return it }
        // 2) EXTRA_APPLICATION (string) — repli.
        intent.getStringExtra(UnifiedPushProtocol.EXTRA_APPLICATION)?.let { return it }
        // 3) Package de l'intent (si le connecteur a setPackage).
        return intent.`package`
    }

    // ============================================================== //
    // Réponses vers le connecteur
    // ============================================================== //
    private fun sendNewEndpoint(context: Context, pkg: String, token: String, endpoint: String) {
        val i = Intent(UnifiedPushProtocol.ACTION_NEW_ENDPOINT).apply {
            setPackage(pkg)
            putExtra(UnifiedPushProtocol.EXTRA_TOKEN, token)
            putExtra(UnifiedPushProtocol.EXTRA_ENDPOINT, endpoint)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        context.sendBroadcast(i)
    }

    private fun sendRegistrationFailed(context: Context, pkg: String, token: String) {
        val i = Intent(UnifiedPushProtocol.ACTION_REGISTRATION_FAILED).apply {
            setPackage(pkg)
            putExtra(UnifiedPushProtocol.EXTRA_TOKEN, token)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        context.sendBroadcast(i)
    }

    private fun sendUnregistered(context: Context, pkg: String, token: String) {
        val i = Intent(UnifiedPushProtocol.ACTION_UNREGISTERED).apply {
            setPackage(pkg)
            putExtra(UnifiedPushProtocol.EXTRA_TOKEN, token)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        context.sendBroadcast(i)
    }
}
