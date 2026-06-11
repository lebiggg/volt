package com.tonnomdeved.volt.data.push.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base Room dédiée aux enregistrements UnifiedPush.
 *
 * Séparée de la base hibernation pour isoler les préoccupations : le cycle de
 * vie des registrations (register/unregister par app tierce) n'a rien à voir
 * avec les politiques d'hibernation.
 */
@Database(
    entities = [PushRegistrationEntity::class],
    version = 1,
    exportSchema = true
)
abstract class PushDatabase : RoomDatabase() {

    abstract fun pushRegistrationDao(): PushRegistrationDao

    companion object {
        private const val DATABASE_NAME = "volt_push.db"

        fun build(context: Context): PushDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PushDatabase::class.java,
                DATABASE_NAME
            ).build()
    }
}
