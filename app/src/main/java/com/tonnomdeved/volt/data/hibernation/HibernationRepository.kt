package com.tonnomdeved.volt.data.hibernation

import com.tonnomdeved.volt.data.hibernation.persistence.HibernationDao
import com.tonnomdeved.volt.data.hibernation.persistence.HibernationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Façade entre le [HibernationController] et la couche persistance Room.
 *
 * **Rôle exclusif** : conversion `Entity ↔ Policy` + exposition de Flow
 * type-safe. Aucune logique métier — toute décision d'hibernation reste
 * dans le [HibernationController].
 *
 * **Pourquoi un Repository séparé du DAO** : le DAO est généré par Room
 * et expose des `Entity` (couche persistance). Le Controller raisonne en
 * `HibernationPolicy` (couche domaine). Le Repository fait le pont — c'est
 * la séparation classique « persistence model vs domain model » qui permet
 * de modifier l'un sans toucher l'autre (refactor Room, schema migration,
 * passage à SQLDelight un jour, etc.).
 */
class HibernationRepository(private val dao: HibernationDao) {

    // ---------------------------------------------------------------- //
    // Lectures observables
    // ---------------------------------------------------------------- //

    /** Flow réactif de toutes les politiques persistées. */
    val policies: Flow<List<HibernationPolicy>> =
        dao.observeAll().map { list -> list.map(HibernationEntity::toDomain) }

    /** Flow réactif du nombre d'apps actuellement en hibernation active. */
    val activeCount: Flow<Int> = dao.observeActiveCount()

    // ---------------------------------------------------------------- //
    // Lectures one-shot
    // ---------------------------------------------------------------- //

    suspend fun getPolicy(packageName: String): HibernationPolicy? =
        dao.getByPackage(packageName)?.toDomain()

    /** Liste des packages actuellement hibernés — utilisé par le sweep. */
    suspend fun getActivePackages(): List<String> = dao.getActivelyHibernatedPackages()

    suspend fun getAllPolicies(): List<HibernationPolicy> =
        dao.getAll().map(HibernationEntity::toDomain)

    // ---------------------------------------------------------------- //
    // Mutations
    // ---------------------------------------------------------------- //

    suspend fun upsert(policy: HibernationPolicy) {
        dao.upsert(HibernationEntity.fromDomain(policy))
    }

    suspend fun delete(packageName: String): Boolean = dao.deleteByPackage(packageName) > 0

    suspend fun recordApplication(packageName: String, timestamp: Long): Boolean =
        dao.recordApplication(packageName, timestamp) > 0

    suspend fun recordWake(packageName: String, timestamp: Long): Boolean =
        dao.recordWake(packageName, timestamp) > 0
}
