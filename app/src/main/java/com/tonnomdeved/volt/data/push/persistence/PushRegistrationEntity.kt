package com.tonnomdeved.volt.data.push.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Enregistrement UnifiedPush : association token ↔ application connecteur.
 *
 * Le `token` (clé primaire) est généré par l'app connecteur et identifie son
 * instance. `packageName` est l'app à qui livrer les messages reçus pour ce
 * token. `endpoint` est l'URL renvoyée à l'app (mémorisée pour audit/debug).
 *
 * Index sur `package_name` pour les opérations de désinscription en masse.
 */
@Entity(
    tableName = "push_registration",
    indices = [Index(value = ["package_name"])]
)
data class PushRegistrationEntity(
    @PrimaryKey
    @ColumnInfo(name = "token")
    val token: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "endpoint")
    val endpoint: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
