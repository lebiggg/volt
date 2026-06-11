package com.tonnomdeved.volt.data.push.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PushRegistrationDao {

    @Upsert
    suspend fun upsert(entity: PushRegistrationEntity)

    @Query("SELECT * FROM push_registration WHERE token = :token LIMIT 1")
    suspend fun getByToken(token: String): PushRegistrationEntity?

    @Query("SELECT package_name FROM push_registration WHERE token = :token LIMIT 1")
    suspend fun packageForToken(token: String): String?

    @Query("DELETE FROM push_registration WHERE token = :token")
    suspend fun deleteByToken(token: String): Int

    @Query("DELETE FROM push_registration WHERE package_name = :packageName")
    suspend fun deleteByPackage(packageName: String): Int

    @Query("SELECT * FROM push_registration ORDER BY created_at DESC")
    fun observeAll(): Flow<List<PushRegistrationEntity>>

    @Query("SELECT COUNT(*) FROM push_registration")
    fun observeCount(): Flow<Int>
}
