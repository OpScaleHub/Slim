package com.opscalehub.slim

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Open-Meteo client (https://open-meteo.com).
 *
 * - Completely key-less and free for non-commercial use.
 * - Only used when the user explicitly enables "Real weather" in Settings
 *   (the launcher stays fully offline otherwise).
 */
class WeatherService(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {

    data class GeoResult(val name: String, val latitude: Float, val longitude: Float)

    data class CurrentWeather(
        val temperature: Double,
        val weatherCode: Int,
        val isDay: Boolean
    ) {
        /** Maps WMO weather codes to a compact emoji + description. */
        fun emoji(): String = when (weatherCode) {
            0 -> if (isDay) "☀️" else "🌙"
            1, 2 -> if (isDay) "⛅" else "☁️"
            3 -> "☁️"
            45, 48 -> "🌫️"
            in 51..57 -> "🌦️"
            in 61..67 -> "🌧️"
            in 71..77 -> "❄️"
            in 80..82 -> "🌧️"
            85, 86 -> "🌨️"
            95, 96, 99 -> "⛈️"
            else -> "🌡️"
        }

        fun description(): String = when (weatherCode) {
            0 -> "Clear"
            1 -> "Mostly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Foggy"
            in 51..57 -> "Drizzle"
            in 61..67 -> "Rain"
            in 71..77 -> "Snow"
            in 80..82 -> "Showers"
            85, 86 -> "Snow showers"
            95, 96, 99 -> "Thunderstorm"
            else -> ""
        }
    }

    /** Resolves a city name to coordinates using the Open-Meteo geocoding API. */
    suspend fun geocodeCity(cityName: String): GeoResult? = withContext(ioDispatcher) {
        try {
            val encoded = URLEncoder.encode(cityName, "UTF-8")
            val json = httpGet(
                "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
            ) ?: return@withContext null
            val results = JSONObject(json).optJSONArray("results") ?: return@withContext null
            if (results.length() == 0) return@withContext null
            val first = results.getJSONObject(0)
            GeoResult(
                name = first.getString("name"),
                latitude = first.getDouble("latitude").toFloat(),
                longitude = first.getDouble("longitude").toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Fetches the current weather for the given coordinates. */
    suspend fun fetchCurrentWeather(
        latitude: Float,
        longitude: Float,
        useFahrenheit: Boolean
    ): CurrentWeather? = withContext(ioDispatcher) {
        try {
            val unit = if (useFahrenheit) "fahrenheit" else "celsius"
            val json = httpGet(
                "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,is_day&temperature_unit=$unit"
            ) ?: return@withContext null
            val current = JSONObject(json).getJSONObject("current")
            CurrentWeather(
                temperature = current.getDouble("temperature_2m"),
                weatherCode = current.getInt("weather_code"),
                isDay = current.optInt("is_day", 1) == 1
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000

        /** Real weather is refreshed at most every 30 minutes. */
        const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
    }
}
