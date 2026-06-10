package com.tonnomdeved.volt.data.hibernation.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tonnomdeved.volt.data.hibernation.HibernationLevel
import com.tonnomdeved.volt.data.hibernation.HibernationPolicy

/**
 * Représentation Room d'une [HibernationPolicy].
 *
 * **Choix techniques** :
 *  - `level` stocké comme `String` (name de l'enum) plutôt que via
 *    TypeConverter. Plus simple, lisible dans le schéma exporté, et
 *    résilient si on ajoute / renomme une valeur d'enum à l'avenir.
 *  - Index sur `level` car le sweep fait régulièrement
 *    `WHERE level != 'OFF'` — l'index élimine un full table scan.
 *  - Aucune colonne `score` : la nocivité est calculée à la volée par
 *    [com.tonnomdeved.volt.data.hibernation.nocivity.NocivityScorer]
 *    (P2). Persister le score ferait dériver la BDD vs la réalité.
 */
@Entity(
    tableName = "hibernation_policy",
    indices = [Index(value = ["level"])]
)
data class HibernationEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "level")
    val level: String,

    @ColumnInfo(name = "user_pinned")
    val userPinned: Boolean,

    @ColumnInfo(name = "user_force_hibernate")
    val userForceHibernate: Boolean,

    @ColumnInfo(name = "last_applied_at")
    val lastAppliedAt: Long,

    @ColumnInfo(name = "last_wake_at")
    val lastWakeAt: Long?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {
    fun toDomain(): HibernationPolicy = HibernationPolicy(
        packageName = packageName,
        level = HibernationLevel.fromName(level),
        userPinned = userPinned,
        userForceHibernate = userForceHibernate,
        lastAppliedAt = lastAppliedAt,
        lastWakeAt = lastWakeAt,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(policy: HibernationPolicy): HibernationEntity = HibernationEntity(
            packageName = policy.packageName,
            level = policy.level.name,
            userPinned = policy.userPinned,
            userForceHibernate = policy.userForceHibernate,
            lastAppliedAt = policy.lastAppliedAt,
            lastWakeAt = policy.lastWakeAt,
            createdAt = policy.createdAt
        )
    }
}
