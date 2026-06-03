package com.opscalehub.slim

import android.content.Context
import android.content.SharedPreferences

/**
 * Central wrapper around SharedPreferences holding every user-configurable
 * launcher option. All feature toggles introduced by the Settings screen
 * read and write through this class.
 */
class SlimPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- Home screen ----
    var showClock: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_CLOCK, value).apply()

    var showDate: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DATE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DATE, value).apply()

    var use24HourFormat: Boolean
        get() = prefs.getBoolean(KEY_24_HOUR, true)
        set(value) = prefs.edit().putBoolean(KEY_24_HOUR, value).apply()

    // ---- Weather ----
    /** One of [WEATHER_OFF], [WEATHER_SIMULATED], [WEATHER_REAL]. */
    var weatherMode: String
        get() = prefs.getString(KEY_WEATHER_MODE, WEATHER_SIMULATED) ?: WEATHER_SIMULATED
        set(value) = prefs.edit().putString(KEY_WEATHER_MODE, value).apply()

    var weatherCity: String
        get() = prefs.getString(KEY_WEATHER_CITY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEATHER_CITY, value).apply()

    var weatherLatitude: Float
        get() = prefs.getFloat(KEY_WEATHER_LAT, Float.NaN)
        set(value) = prefs.edit().putFloat(KEY_WEATHER_LAT, value).apply()

    var weatherLongitude: Float
        get() = prefs.getFloat(KEY_WEATHER_LON, Float.NaN)
        set(value) = prefs.edit().putFloat(KEY_WEATHER_LON, value).apply()

    var useFahrenheit: Boolean
        get() = prefs.getBoolean(KEY_WEATHER_FAHRENHEIT, false)
        set(value) = prefs.edit().putBoolean(KEY_WEATHER_FAHRENHEIT, value).apply()

    /** Cached last successful weather text so the header never flashes empty. */
    var lastWeatherText: String
        get() = prefs.getString(KEY_WEATHER_CACHE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEATHER_CACHE, value).apply()

    var lastWeatherFetchTime: Long
        get() = prefs.getLong(KEY_WEATHER_CACHE_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_WEATHER_CACHE_TIME, value).apply()

    // ---- Search ----
    var searchHistoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_HISTORY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SEARCH_HISTORY_ENABLED, value).apply()

    /**
     * Recent apps launched from search results, most recent first.
     * Stored as package names separated by [HISTORY_SEPARATOR].
     */
    fun getSearchHistory(): List<String> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(HISTORY_SEPARATOR).filter { it.isNotEmpty() }
    }

    fun addToSearchHistory(packageName: String) {
        if (!searchHistoryEnabled) return
        val history = getSearchHistory().toMutableList()
        history.remove(packageName)
        history.add(0, packageName)
        val trimmed = history.take(MAX_HISTORY_SIZE)
        prefs.edit().putString(KEY_SEARCH_HISTORY, trimmed.joinToString(HISTORY_SEPARATOR)).apply()
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    // ---- Gestures ----
    var swipeUpForSearch: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_UP_SEARCH, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_UP_SEARCH, value).apply()

    companion object {
        private const val PREFS_NAME = "slim_launcher_prefs"
        private const val HISTORY_SEPARATOR = "|"
        const val MAX_HISTORY_SIZE = 8

        const val WEATHER_OFF = "off"
        const val WEATHER_SIMULATED = "simulated"
        const val WEATHER_REAL = "real"

        private const val KEY_SHOW_CLOCK = "show_clock"
        private const val KEY_SHOW_DATE = "show_date"
        private const val KEY_24_HOUR = "use_24_hour"
        private const val KEY_WEATHER_MODE = "weather_mode"
        private const val KEY_WEATHER_CITY = "weather_city"
        private const val KEY_WEATHER_LAT = "weather_lat"
        private const val KEY_WEATHER_LON = "weather_lon"
        private const val KEY_WEATHER_FAHRENHEIT = "weather_fahrenheit"
        private const val KEY_WEATHER_CACHE = "weather_cache"
        private const val KEY_WEATHER_CACHE_TIME = "weather_cache_time"
        private const val KEY_SEARCH_HISTORY_ENABLED = "search_history_enabled"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_SWIPE_UP_SEARCH = "swipe_up_search"
    }
}
