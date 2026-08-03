package org.openhab.habdroid.wear.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

/** Available tile color themes with their ARGB color values. */
enum class TileTheme(val displayName: String, val color: Int, val glowOpacity: Float) {
    AMBER("Amber", 0xFFFFB300.toInt(), 0.6f),
    BLUE("Blue", 0xFF42A5F5.toInt(), 0.55f),
    GREEN("Green", 0xFF66BB6A.toInt(), 0.55f),
    PURPLE("Purple", 0xFFAB47BC.toInt(), 0.55f),
    RED("Red", 0xFFF44336.toInt(), 0.55f);

    companion object {
        fun fromName(name: String): TileTheme =
            entries.find { it.name == name } ?: AMBER
    }
}

/** Persists the user's selected tile theme color to DataStore preferences. */
@Singleton
class ThemeStore(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val KEY_THEME = stringPreferencesKey("tile_theme")
    }

    val theme: Flow<TileTheme> = dataStore.data.map { prefs ->
        TileTheme.fromName(prefs[KEY_THEME] ?: TileTheme.AMBER.name)
    }

    suspend fun getTheme(): TileTheme {
        return theme.first()
    }

    suspend fun setTheme(theme: TileTheme) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME] = theme.name
        }
    }
}
