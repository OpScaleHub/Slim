package com.opscalehub.slim

import android.app.AlertDialog
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Slim Settings: feature toggles, weather configuration, search options,
 * gesture options, system shortcuts, and the About/Contact section.
 *
 * Reached by tapping the "Slim" entry inside the launcher's own app list.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SlimPreferences
    private lateinit var repository: AppRepository
    private val weatherService = WeatherService()

    // SAF pickers for settings backup/restore
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) exportBackup(uri)
        }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importBackup(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = SlimPreferences(this)
        repository = AppRepository(this, AppDatabase.getDatabase(this).appDao())

        bindHomeSection()
        bindAppearanceSection()
        bindWeatherSection()
        bindSearchSection()
        bindGesturesSection()
        bindBackupSection()
        bindSystemSection()
        bindAboutSection()
    }

    private fun bindAppearanceSection() {
        val switchIcons = findViewById<SwitchMaterial>(R.id.switchShowAppIcons)
        switchIcons.isChecked = prefs.showAppIcons
        switchIcons.setOnCheckedChangeListener { _, checked -> prefs.showAppIcons = checked }

        // Background mode radio group
        val radioBg = findViewById<RadioGroup>(R.id.radioBackgroundMode)
        radioBg.check(when (prefs.backgroundMode) {
            SlimPreferences.BG_TRANSPARENT -> R.id.radioBgTransparent
            SlimPreferences.BG_SOLID_BLACK -> R.id.radioBgSolidBlack
            else -> R.id.radioBgDimmed
        })
        radioBg.setOnCheckedChangeListener { _, id ->
            prefs.backgroundMode = when (id) {
                R.id.radioBgTransparent -> SlimPreferences.BG_TRANSPARENT
                R.id.radioBgSolidBlack -> SlimPreferences.BG_SOLID_BLACK
                else -> SlimPreferences.BG_DIMMED
            }
        }

        val switchImmersive = findViewById<SwitchMaterial>(R.id.switchImmersiveMode)
        switchImmersive.isChecked = prefs.immersiveMode
        switchImmersive.setOnCheckedChangeListener { _, checked -> prefs.immersiveMode = checked }

        findViewById<TextView>(R.id.btnHiddenApps).setOnClickListener {
            showHiddenAppsDialog()
        }
    }

    /** Lists hidden apps; tapping one unhides it. */
    private fun showHiddenAppsDialog() {
        lifecycleScope.launch {
            val hiddenApps = repository.getHiddenApps()
            if (hiddenApps.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.settings_no_hidden_apps, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = hiddenApps.map { it.displayLabel }.toTypedArray()
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle(getString(R.string.settings_hidden_apps) + " — " + getString(R.string.settings_unhide_hint))
                .setItems(labels) { _, which ->
                    lifecycleScope.launch {
                        repository.setAppHidden(hiddenApps[which].id, false)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun bindBackupSection() {
        findViewById<TextView>(R.id.btnExportSettings).setOnClickListener {
            exportLauncher.launch("slim-backup.json")
        }
        findViewById<TextView>(R.id.btnImportSettings).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
        }
    }

    /** Writes preferences + app customizations (favorites/hidden/renames) to a JSON file. */
    private fun exportBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val backup = JSONObject()
                backup.put("preferences", prefs.exportToJson())

                val apps = JSONArray()
                for (app in repository.getCustomizedApps()) {
                    apps.put(JSONObject().apply {
                        put("id", app.id)
                        put("isFavorite", app.isFavorite)
                        put("isHidden", app.isHidden)
                        put("customLabel", app.customLabel ?: JSONObject.NULL)
                    })
                }
                backup.put("apps", apps)

                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(backup.toString(2).toByteArray())
                }
                Toast.makeText(this@SettingsActivity, R.string.settings_export_done, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.settings_export_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Restores preferences + app customizations from a backup file. */
    private fun importBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: throw IllegalStateException("Empty file")

                val backup = JSONObject(content)
                prefs.importFromJson(backup.getJSONObject("preferences"))

                val apps = backup.optJSONArray("apps") ?: JSONArray()
                for (i in 0 until apps.length()) {
                    val app = apps.getJSONObject(i)
                    val id = app.getString("id")
                    repository.setAppAsFavorite(id, app.optBoolean("isFavorite", false))
                    repository.setAppHidden(id, app.optBoolean("isHidden", false))
                    val label = if (app.isNull("customLabel")) null else app.getString("customLabel")
                    repository.setCustomLabel(id, label)
                }

                Toast.makeText(this@SettingsActivity, R.string.settings_import_done, Toast.LENGTH_LONG).show()
                recreate() // Reload toggles with imported values
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.settings_import_failed, Toast.LENGTH_LONG).show()
            }
        }
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

        val switchSwipeDown = findViewById<SwitchMaterial>(R.id.switchSwipeDownNotifications)
        switchSwipeDown.isChecked = prefs.swipeDownForNotifications
        switchSwipeDown.setOnCheckedChangeListener { _, checked ->
            prefs.swipeDownForNotifications = checked
        }
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
