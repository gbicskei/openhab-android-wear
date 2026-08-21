package org.openhab.habdroid.wear.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

/** Available tile color themes with their ARGB color values (M3 Expressive tone-80 for dark theme). */
enum class TileTheme(val displayName: String, val color: Int, val glowOpacity: Float) {
    AMBER("Amber", 0xFFFFB950.toInt(), 0.6f),
    BLUE("Blue", 0xFFA8C8FF.toInt(), 0.55f),
    GREEN("Green", 0xFF8AD88E.toInt(), 0.55f),
    PURPLE("Purple", 0xFFD4BBFF.toInt(), 0.55f),
    RED("Red", 0xFFFFB4AB.toInt(), 0.55f);

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
    private val KEY_THEME = stringPreferencesKey("tile_theme")

    val theme: Flow<TileTheme> = dataStore.data.map { prefs ->
        TileTheme.fromName(prefs[KEY_THEME] ?: TileTheme.AMBER.name)
    }

    suspend fun getTheme(): TileTheme {
        return theme.first()
    }

    /**
     * Synchronous read for use in Application.onCreate() to warm the cache.
     * DataStore caches in memory after first read, so this is fast (~1ms).
     * Also updates the in-memory cache for other activities.
     */
    fun getThemeBlocking(): TileTheme {
        val t = runBlocking { theme.first() }
        cachedTheme = t
        return t
    }

    suspend fun setTheme(theme: TileTheme) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME] = theme.name
        }
        cachedTheme = theme
    }

    companion object {
        /**
         * In-memory cached theme for instant access without coroutines.
         * Updated on every read/write. Used as the default for WearOHTheme
         * so activities render the correct color on the first frame.
         */
        @Volatile
        var cachedTheme: TileTheme = TileTheme.AMBER
            internal set
    }
}
