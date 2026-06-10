package com.tonnomdeved.volt.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "volt_prefs")

/**
 * Facade DataStore - preferences simples de Volt.
 *
 * Source de verite des reglages "legers" : URL du serveur push, seuils
 * d'auto-hibernation, theme. Les politiques d'hibernation par app vivent
 * dans Room (HibernationDatabase), pas ici.
 *
 * NB : volontairement zero dependance Hilt/Koin - instanciation manuelle
 * via `Context.applicationContext`.
 */
class VoltPreferences(private val context: Context) {

    /** Choix de thème — suit le système par défaut. */
    enum class ThemeMode { SYSTEM, LIGHT, DARK;
        companion object {
            fun fromName(name: String?): ThemeMode =
                entries.firstOrNull { it.name == name } ?: SYSTEM
        }
    }

    private object Keys {
        val PUSH_SERVER_URL = stringPreferencesKey("push_server_url")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THRESHOLD_SOFT = intPreferencesKey("threshold_soft")
        val THRESHOLD_MEDIUM = intPreferencesKey("threshold_medium")
        val THRESHOLD_HARD = intPreferencesKey("threshold_hard")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUTO_HIBERNATION = booleanPreferencesKey("auto_hibernation")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
    }

    // ---------- Push ----------
    val pushServerUrl: Flow<String> = context.dataStore.data.map { it[Keys.PUSH_SERVER_URL].orEmpty() }
    suspend fun setPushServerUrl(url: String) {
        context.dataStore.edit { it[Keys.PUSH_SERVER_URL] = url.trim() }
    }

    // ---------- Thème ----------
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.fromName(it[Keys.THEME_MODE])
    }
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }
    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    // ---------- Seuils d'auto-hibernation ----------
    val thresholdSoft: Flow<Int> = context.dataStore.data.map { it[Keys.THRESHOLD_SOFT] ?: 30 }
    val thresholdMedium: Flow<Int> = context.dataStore.data.map { it[Keys.THRESHOLD_MEDIUM] ?: 60 }
    val thresholdHard: Flow<Int> = context.dataStore.data.map { it[Keys.THRESHOLD_HARD] ?: 85 }

    suspend fun setThresholds(soft: Int, medium: Int, hard: Int) {
        context.dataStore.edit {
            it[Keys.THRESHOLD_SOFT] = soft.coerceIn(0, 100)
            it[Keys.THRESHOLD_MEDIUM] = medium.coerceIn(soft, 100)
            it[Keys.THRESHOLD_HARD] = hard.coerceIn(medium, 100)
        }
    }

    // ---------- Automatisation ----------
    val autoHibernationEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_HIBERNATION] ?: false }
    suspend fun setAutoHibernation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_HIBERNATION] = enabled }
    }

    val startOnBoot: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.START_ON_BOOT] ?: false }
    suspend fun setStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[Keys.START_ON_BOOT] = enabled }
    }
}

