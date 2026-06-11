package com.tonnomdeved.volt.network

import android.content.Context
import android.content.Intent
import android.util.Log
import com.tonnomdeved.volt.BatteryCommandService
import com.tonnomdeved.volt.BuildConfig
import com.tonnomdeved.volt.data.VoltStateBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Hub UnifiedPush : maintient une connexion WebSocket sortante vers le
 * distributeur choisi par l'utilisateur, route les payloads vers le
 * [BatteryCommandService] via un broadcast intra-process sécurisé.
 *
 * **Garanties (cf. audit) :**
 * - URL `wss://` obligatoire (rejet à l'init si `ws://`).
 * - Aucun log du contenu des messages, même en DEBUG.
 * - Backoff exponentiel + full-jitter pour éviter le thundering herd.
 * - Fermeture explicite de toute WebSocket résiduelle avant reconnexion.
 * - `OkHttp dispatcher + connection pool` libérés à l'arrêt (pas de thread zombie).
 */
class PushConnectionManager(
    context: Context,
    private val serverUrl: String
) {
    private companion object {
        private const val TAG = "VoltPushManager"
        private const val INITIAL_BACKOFF_MS = 5_000L
        private const val MAX_BACKOFF_MS = 600_000L         // 10 min
        private const val PING_INTERVAL_SEC = 240L          // 4 min — sera adaptatif post-MVP
        private const val MAX_BACKOFF_SHIFT = 8             // 2^8 = 256× INITIAL = 21 min, clamp à MAX
        private const val WS_NORMAL_CLOSURE = 1000
    }

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var attempt = 0

    fun startListening() {
        require(serverUrl.startsWith("wss://")) {
            "URL non TLS refusée — wss:// requis"
        }
        if (client != null) {
            if (BuildConfig.DEBUG) Log.w(TAG, "startListening appelé deux fois — ignoré")
            return
        }
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)         // indispensable pour WS
            .pingInterval(PING_INTERVAL_SEC, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)                // on gère le retry nous-mêmes
            // CertificatePinner.Builder().add("push.exemple.org", "sha256/AAAA…").build()
            // → à activer en prod avec un pin réel.
            .build()
        connect()
    }

    private fun connect() {
        // Toute WebSocket résiduelle est annulée avant d'en créer une nouvelle —
        // empêche l'accumulation silencieuse de connexions parallèles.
        webSocket?.cancel()
        webSocket = null

        VoltStateBus.updatePushStatus(VoltStateBus.PushStatus.Connecting)

        val request = Request.Builder().url(serverUrl).build()
        val listener = object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                if (BuildConfig.DEBUG) Log.d(TAG, "WS open")
                attempt = 0
                VoltStateBus.updatePushStatus(VoltStateBus.PushStatus.Connected)
            }

            // PAS de log du contenu — privacy by design.
            override fun onMessage(ws: WebSocket, text: String) {
                routePush(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                if (BuildConfig.DEBUG) Log.d(TAG, "WS closing code=$code")
                ws.close(WS_NORMAL_CLOSURE, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (BuildConfig.DEBUG) Log.d(TAG, "WS closed code=$code")
                VoltStateBus.updatePushStatus(VoltStateBus.PushStatus.Disconnected)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (BuildConfig.DEBUG) Log.w(TAG, "WS failure: ${t.javaClass.simpleName}")
                VoltStateBus.updatePushStatus(
                    VoltStateBus.PushStatus.Error(t.javaClass.simpleName)
                )
                scheduleReconnect()
            }
        }
        webSocket = client?.newWebSocket(request, listener)
    }

    /**
     * Backoff full-jitter (pattern AWS) :
     *   delay = random(0, min(MAX, INITIAL * 2^attempt))
     * Évite la cohorte synchronisée de clients à T+5s, T+10s, T+20s.
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val shift = attempt.coerceAtMost(MAX_BACKOFF_SHIFT)
            val capped = (INITIAL_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
            val sleepMs = Random.nextLong(0, capped + 1)

            VoltStateBus.updatePushStatus(VoltStateBus.PushStatus.Backoff(sleepMs / 1000))
            delay(sleepMs)

            if (isActive) {
                attempt++
                connect()
            }
        }
    }

    /**
     * Routage interne du push. Le payload n'est jamais inspecté avant d'être
     * forwardé au Service, et n'est jamais loggué.
     *
     * Format wire : `<token>:<message>` (UnifiedPush) — rétro-compatible avec
     * `<package>:<message>`. La résolution token→package se fait dans le Service
     * (qui a accès au repository des registrations).
     */
    private fun routePush(payload: String) {
        val parsed = com.tonnomdeved.volt.data.push.UnifiedPushProtocol.parseWirePayload(payload)
            ?: return
        val (key, msg) = parsed

        val intent = Intent(BatteryCommandService.ACTION_DELIVER_PUSH).apply {
            putExtra(BatteryCommandService.EXTRA_TARGET_PACKAGE, key)
            putExtra(BatteryCommandService.EXTRA_MESSAGE_DATA, msg)
            // Restriction critique : broadcast purement intra-process.
            setPackage(appContext.packageName)
        }
        appContext.sendBroadcast(intent)
    }

    fun stopListening() {
        reconnectJob?.cancel()
        webSocket?.close(WS_NORMAL_CLOSURE, "stop")
        webSocket = null
        // Libère les pools OkHttp pour éviter les threads zombies après stop.
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        client = null
        scope.cancel()
        VoltStateBus.updatePushStatus(VoltStateBus.PushStatus.Disconnected)
    }
}
