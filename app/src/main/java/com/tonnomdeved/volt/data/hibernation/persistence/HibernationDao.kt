package com.tonnomdeved.volt.data.hibernation.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * DAO Room pour la table `hibernation_policy`.
 *
 * **Conventions** :
 *  - Toutes les opérations de mutation sont `suspend` → impossible de bloquer
 *    le main thread par erreur.
 *  - Les lectures observables exposent `Flow` (Room génère le ré-émetteur sur
 *    invalidation). Les lectures one-shot sont `suspend`.
 *  - Utilisation de `@Upsert` plutôt qu'`@Insert(REPLACE)` car plus expressif
 *    et conserve les triggers `onConflict` standard SQLite.
 */
@Dao
interface HibernationDao {

    // ---------------------------------------------------------------- //
    // Mutations
    // ---------------------------------------------------------------- //
    @Upsert
    suspend fun upsert(entity: HibernationEntity)

    @Query("DELETE FROM hibernation_policy WHERE package_name = :packageName")
    suspend fun deleteByPackage(packageName: String): Int

    @Query("DELETE FROM hibernation_policy")
    suspend fun clear()

    // ---------------------------------------------------------------- //
    // Lectures one-shot
    // ---------------------------------------------------------------- //
    @Query("SELECT * FROM hibernation_policy WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): HibernationEntity?

    /** Tous les packages avec une politique active (≠ OFF) — utilisé par le sweep. */
    @Query("SELECT package_name FROM hibernation_policy WHERE level != 'OFF'")
    suspend fun getActivelyHibernatedPackages(): List<String>

    @Query("SELECT * FROM hibernation_policy")
    suspend fun getAll(): List<HibernationEntity>

    // ---------------------------------------------------------------- //
    // Lectures observables (Flow)
    // ---------------------------------------------------------------- //
    @Query("SELECT * FROM hibernation_policy ORDER BY package_name ASC")
    fun observeAll(): Flow<List<HibernationEntity>>

    @Query("SELECT COUNT(*) FROM hibernation_policy WHERE level != 'OFF'")
    fun observeActiveCount(): Flow<Int>

    // ---------------------------------------------------------------- //
    // Mises à jour atomiques fines (évite read-modify-write côté Kotlin)
    // ---------------------------------------------------------------- //
    @Query("""
        UPDATE hibernation_policy
        SET last_applied_at = :timestamp
        WHERE package_name = :packageName
    """)
    suspend fun recordApplication(packageName: String, timestamp: Long): Int

    @Query("""
        UPDATE hibernation_policy
        SET last_wake_at = :timestamp
        WHERE package_name = :packageName
    """)
    suspend fun recordWake(packageName: String, timestamp: Long): Int
}
