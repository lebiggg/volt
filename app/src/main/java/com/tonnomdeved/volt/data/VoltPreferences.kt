package com.tonnomdeved.volt.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "volt_prefs")

/**
 * Facade DataStore - preferences simples de Volt.
 *
 * Ne contient plus que l'URL du serveur push : la liste des apps restreintes
 * (ancien "Deep Sleep" DataStore) a ete migree vers Room
 * ([com.tonnomdeved.volt.data.hibernation.persistence.HibernationDatabase])
 * qui est desormais l'unique source de verite des politiques d'hibernation.
 *
 * NB : volontairement zero dependance Hilt/Koin - instanciation manuelle
 * via `Context.applicationContext`.
 */
class VoltPreferences(private val context: Context) {

    private object Keys {
        val PUSH_SERVER_URL: Preferences.Key<String> = stringPreferencesKey("push_server_url")
    }

    val pushServerUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.PUSH_SERVER_URL].orEmpty()
    }

    suspend fun setPushServerUrl(url: String) {
        context.dataStore.edit { it[Keys.PUSH_SERVER_URL] = url.trim() }
    }
}
