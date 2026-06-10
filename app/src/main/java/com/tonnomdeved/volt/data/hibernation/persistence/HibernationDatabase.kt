package com.tonnomdeved.volt.data.hibernation.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de données Room dédiée à l'hibernation.
 *
 * **Choix d'isolation** : une BDD séparée plutôt que de joindre une table
 * dans une éventuelle base globale. Justifications :
 *  - L'hibernation a son cycle de vie propre (sweep, wakes, settings) qui
 *    ne se mixe pas avec d'autres données utilisateur.
 *  - Migration / drop isolée : si jamais on doit reset, on n'efface pas
 *    autre chose par effet de bord.
 *  - Possibilité future d'ajouter un mot de passe / chiffrement (SQLCipher)
 *    sur cette base sans toucher au reste.
 *
 * **Instanciation** : strictement gérée par [VoltContainer] (singleton
 * applicatif). Aucun autre code ne doit appeler [Room.databaseBuilder]
 * directement — sinon on risque deux connexions concurrentes.
 */
@Database(
    entities = [HibernationEntity::class],
    version = 1,
    exportSchema = true
)
abstract class HibernationDatabase : RoomDatabase() {

    abstract fun hibernationDao(): HibernationDao

    companion object {
        private const val DATABASE_NAME = "volt_hibernation.db"

        /**
         * Construction de la base. Appelée **une seule fois** par
         * [com.tonnomdeved.volt.VoltContainer].
         */
        fun build(context: Context): HibernationDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                HibernationDatabase::class.java,
                DATABASE_NAME
            )
                // Pas de fallbackToDestructiveMigration() : on veut être obligé
                // d'écrire les migrations explicitement plutôt que de perdre
                // silencieusement les politiques utilisateurs en cas de bump.
                .build()
    }
}
