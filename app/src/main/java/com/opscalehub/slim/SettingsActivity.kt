package com.opscalehub.slim

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

/**
 * Slim Settings: feature toggles, weather configuration, search options,
 * gesture options, system shortcuts, and the About/Contact section.
 *
 * Reached by tapping the "Slim" entry inside the launcher's own app list.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SlimPreferences
    private val weatherService = WeatherService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = SlimPreferences(this)

        bindHomeSection()
        bindWeatherSection()
        bindSearchSection()
        bindGesturesSection()
        bindSystemSection()
        bindAboutSection()
    }

    private fun bindHomeSection() {
        val switchClock = findViewById<SwitchMaterial>(R.id.switchShowClock)
        val switchDate = findViewById<SwitchMaterial>(R.id.switchShowDate)
        val switch24h = findViewById<SwitchMaterial>(R.id.switch24Hour)

        switchClock.isChecked = prefs.showClock
        switchDate.isChecked = prefs.showDate
        switch24h.isChecked = prefs.use24HourFormat

        switchClock.setOnCheckedChangeListener { _, checked -> prefs.showClock = checked }
        switchDate.setOnCheckedChangeListener { _, checked -> prefs.showDate = checked }
        switch24h.setOnCheckedChangeListener { _, checked -> prefs.use24HourFormat = checked }
    }

    private fun bindWeatherSection() {
        val radioGroup = findViewById<RadioGroup>(R.id.radioWeatherMode)
        val cityRow = findViewById<View>(R.id.cityInputRow)
        val editCity = findViewById<EditText>(R.id.editCity)
        val btnSaveCity = findViewById<Button>(R.id.btnSaveCity)
        val switchFahrenheit = findViewById<SwitchMaterial>(R.id.switchFahrenheit)
        val privacyNote = findViewById<TextView>(R.id.txtWeatherPrivacyNote)

        // Restore current state
        val checkedId = when (prefs.weatherMode) {
            SlimPreferences.WEATHER_OFF -> R.id.radioWeatherOff
            SlimPreferences.WEATHER_REAL -> R.id.radioWeatherReal
            else -> R.id.radioWeatherSimulated
        }
        radioGroup.check(checkedId)
        editCity.setText(prefs.weatherCity)
        switchFahrenheit.isChecked = prefs.useFahrenheit

        fun updateRealWeatherVisibility(isReal: Boolean) {
            cityRow.visibility = if (isReal) View.VISIBLE else View.GONE
            privacyNote.visibility = if (isReal) View.VISIBLE else View.GONE
        }
        updateRealWeatherVisibility(prefs.weatherMode == SlimPreferences.WEATHER_REAL)

        radioGroup.setOnCheckedChangeListener { _, id ->
            prefs.weatherMode = when (id) {
                R.id.radioWeatherOff -> SlimPreferences.WEATHER_OFF
                R.id.radioWeatherReal -> SlimPreferences.WEATHER_REAL
                else -> SlimPreferences.WEATHER_SIMULATED
            }
            // Force a refresh on next launcher resume
            prefs.lastWeatherFetchTime = 0L
            updateRealWeatherVisibility(id == R.id.radioWeatherReal)
        }

        btnSaveCity.setOnClickListener {
            val city = editCity.text.toString().trim()
            if (city.isEmpty()) return@setOnClickListener
            btnSaveCity.isEnabled = false
            lifecycleScope.launch {
                val result = weatherService.geocodeCity(city)
                btnSaveCity.isEnabled = true
                if (result != null) {
                    prefs.weatherCity = result.name
                    prefs.weatherLatitude = result.latitude
                    prefs.weatherLongitude = result.longitude
                    prefs.lastWeatherFetchTime = 0L
                    editCity.setText(result.name)
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.settings_weather_city_saved, result.name),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@SettingsActivity,
                        R.string.settings_weather_city_not_found,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        switchFahrenheit.setOnCheckedChangeListener { _, checked ->
            prefs.useFahrenheit = checked
            prefs.lastWeatherFetchTime = 0L
        }
    }

    private fun bindSearchSection() {
        val switchHistory = findViewById<SwitchMaterial>(R.id.switchSearchHistory)
        val btnClear = findViewById<TextView>(R.id.btnClearHistory)

        switchHistory.isChecked = prefs.searchHistoryEnabled
        switchHistory.setOnCheckedChangeListener { _, checked ->
            prefs.searchHistoryEnabled = checked
            if (!checked) prefs.clearSearchHistory()
        }

        btnClear.setOnClickListener {
            prefs.clearSearchHistory()
            Toast.makeText(this, R.string.settings_history_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindGesturesSection() {
        val switchSwipeUp = findViewById<SwitchMaterial>(R.id.switchSwipeUpSearch)
        switchSwipeUp.isChecked = prefs.swipeUpForSearch
        switchSwipeUp.setOnCheckedChangeListener { _, checked -> prefs.swipeUpForSearch = checked }
    }

    private fun bindSystemSection() {
        findViewById<TextView>(R.id.btnDefaultLauncher).setOnClickListener {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
        findViewById<TextView>(R.id.btnNotificationAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(this, R.string.settings_notification_hint, Toast.LENGTH_LONG).show()
        }
    }

    private fun bindAboutSection() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        findViewById<TextView>(R.id.txtVersion).text = getString(R.string.about_version, versionName)

        findViewById<TextView>(R.id.btnGithub).setOnClickListener {
            openUrl(getString(R.string.url_github))
        }
        findViewById<TextView>(R.id.btnWebsite).setOnClickListener {
            openUrl(getString(R.string.url_website))
        }
        findViewById<TextView>(R.id.btnReportBug).setOnClickListener {
            openUrl(getString(R.string.url_issues))
        }
        findViewById<TextView>(R.id.btnContact).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:" + getString(R.string.contact_email))
                putExtra(Intent.EXTRA_SUBJECT, "Slim Launcher feedback")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.contact_email), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show()
        }
    }
}
