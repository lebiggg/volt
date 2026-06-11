package com.tonnomdeved.volt.data.push

import com.tonnomdeved.volt.data.push.persistence.PushRegistrationDao
import com.tonnomdeved.volt.data.push.persistence.PushRegistrationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Façade des enregistrements UnifiedPush. Source de vérité du mapping
 * token ↔ application connecteur.
 */
class PushRegistrationRepository(private val dao: PushRegistrationDao) {

    val count: Flow<Int> = dao.observeCount()
    val all: Flow<List<PushRegistrationEntity>> = dao.observeAll()

    suspend fun register(token: String, packageName: String, endpoint: String, now: Long) {
        dao.upsert(
            PushRegistrationEntity(
                token = token,
                packageName = packageName,
                endpoint = endpoint,
                createdAt = now
            )
        )
    }

    /** Package cible pour un token reçu sur le WebSocket. */
    suspend fun packageForToken(token: String): String? = dao.packageForToken(token)

    suspend fun unregisterToken(token: String): Boolean = dao.deleteByToken(token) > 0

    suspend fun unregisterPackage(packageName: String): Int = dao.deleteByPackage(packageName)
}
