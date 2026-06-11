package com.tonnomdeved.volt.data.push

/**
 * Constantes et helpers purs du protocole UnifiedPush, côté **distributeur**.
 *
 * Référence : spécification UnifiedPush (org.unifiedpush.android.*).
 * Volt agit comme distributeur : il reçoit les `REGISTER`/`UNREGISTER` des
 * apps connecteurs, leur renvoie un `NEW_ENDPOINT`, et leur livre les
 * `MESSAGE` reçus via le WebSocket.
 *
 * Les helpers ci-dessous sont purs (pas de dépendance Android) → testables.
 */
object UnifiedPushProtocol {

    // ---- Actions reçues du connecteur (app → distributeur) ----
    const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"

    // ---- Actions émises vers le connecteur (distributeur → app) ----
    const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
    const val ACTION_REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"
    const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"
    const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"

    // ---- Extras ----
    const val EXTRA_TOKEN = "token"
    const val EXTRA_ENDPOINT = "endpoint"
    const val EXTRA_APPLICATION = "application"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_BYTES_MESSAGE = "bytesMessage"
    const val EXTRA_MESSAGE_ID = "id"
    /** PendingIntent fourni par le connecteur pour vérifier son identité. */
    const val EXTRA_PI = "pi"

    /**
     * Construit l'endpoint renvoyé à l'app connecteur.
     *
     * Convention Volt (documentée dans le README) : l'endpoint dérive de l'URL
     * WebSocket configurée par l'utilisateur, en HTTP(S), avec le token en
     * paramètre. Le serveur de push de l'utilisateur reçoit un POST sur cet
     * endpoint et forwarde `token:message` sur le WebSocket vers Volt.
     *
     *   wss://push.example.org/ws  +  tok123
     *     → https://push.example.org/UP?token=tok123
     *
     * @return null si l'URL serveur est vide ou non `wss://`/`ws://`.
     */
    fun endpointFor(serverUrl: String, token: String): String? {
        if (token.isBlank()) return null
        val httpBase = when {
            serverUrl.startsWith("wss://") -> "https://" + serverUrl.removePrefix("wss://")
            serverUrl.startsWith("ws://")  -> "http://"  + serverUrl.removePrefix("ws://")
            else -> return null
        }
        // Retire un éventuel chemin terminal "/ws" et le slash final, puis ajoute /UP.
        val trimmed = httpBase.trimEnd('/').removeSuffix("/ws")
        return "$trimmed/UP?token=$token"
    }

    /**
     * Parse un message reçu sur le WebSocket.
     *
     * Format attendu (serveur → Volt) : `<token>:<message>` (split sur le
     * premier `:`). Rétro-compatible avec l'ancien format `<package>:<message>`
     * — le routeur tentera d'abord une résolution par token, puis par package.
     *
     * @return Pair(tokenOrPackage, message) ou null si format invalide.
     */
    fun parseWirePayload(payload: String): Pair<String, String>? {
        val idx = payload.indexOf(':')
        if (idx <= 0 || idx == payload.length - 1) return null
        val key = payload.substring(0, idx).trim()
        val msg = payload.substring(idx + 1)
        if (key.isEmpty()) return null
        return key to msg
    }
}
