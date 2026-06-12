package com.opscalehub.slim

import android.app.AlertDialog
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), WaveGestureView.OnLetterSelectedListener, NotificationRegistry.NotificationUpdateListener {

    companion object {
        private const val SETTINGS_LETTER = "⚙"
        // Minimum gap between real-weather fetch attempts, including failed ones.
        // The clock ticks every second and used to re-fire a request on every
        // tick until one succeeded — that self-inflicted hammering is what
        // tripped Open-Meteo's "too many requests" limit and left it stuck.
        private const val WEATHER_RETRY_INTERVAL_MS = 60_000L
    }

    private lateinit var appRecyclerView: RecyclerView
    private lateinit var headerLayout: View
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var waveGestureView: WaveGestureView
    private lateinit var searchEditText: EditText
    private lateinit var searchPanel: View
    private lateinit var searchScrim: View
    private lateinit var historyScroll: HorizontalScrollView
    private lateinit var txtNoResults: TextView
    private lateinit var txtClock: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtWeather: TextView
    private lateinit var txtLetterPopup: TextView
    private lateinit var statusInfoRow: View
    private lateinit var txtNotificationSummary: TextView
    private lateinit var txtBattery: TextView
    private var packageChangeReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null

    private lateinit var db: AppDatabase
    private lateinit var repository: AppRepository
    private lateinit var prefs: SlimPreferences
    private lateinit var adapter: AppListAdapter
    private lateinit var searchAdapter: AppListAdapter
    private val weatherService = WeatherService()

    private var allApps = listOf<AppItem>()
    private var favorites = listOf<AppItem>()
    private var filteredList = listOf<AdapterItem>()

    private val clockTimer = Timer()
    private lateinit var gestureDetector: GestureDetector

    // State controlling list visibility
    private var isAlphabetScrubbing = false
    // Set on ACTION_DOWN when the touch starts on the alphabet index so the
    // swipe-up-for-search gesture never fires while scrubbing letters.
    private var touchStartedOnWave = false
    // Track last down position for reliable swipe detection.
    // GestureDetector.onFling is unreliable when RecyclerView consumes events.
    private var lastDownX = 0f
    private var lastDownY = 0f
    // Actual height of the system gesture nav zone — read from window insets
    private var systemGestureHeight = 0
    private var weatherFetchInProgress = false
    // Prompt to become the default launcher at most once per process so we
    // nudge after a fresh install without nagging on every resume.
    private var defaultHomePrompted = false
    // The RoleManager HOME request must run for-result so the system can read
    // our calling package; plain startActivity() gives it a null caller and the
    // dialog bails instantly. The result is ignored — onResume re-checks state.
    private val defaultHomeLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { }
    // Last time we *attempted* a real-weather fetch (success or failure), used
    // to throttle retries so a failing endpoint doesn't get hammered.
    private var lastWeatherAttemptTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val returnToFavoritesRunnable = Runnable {
        exitAlphabetMode()
    }
    // Retry runnables for app-refresh (F-Droid/Revanced install timing).
    // Each fires once; retry2 is the final attempt — no infinite loop.
    private var refreshRetryCount = 0
    private val refreshRetry1 = Runnable {
        refreshRetryCount++
        lifecycleScope.launch { repository.refreshApps() }
    }
    private val refreshRetry2 = Runnable {
        refreshRetryCount++
        lifecycleScope.launch { repository.refreshApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        appRecyclerView = findViewById(R.id.appRecyclerView)
        headerLayout = findViewById(R.id.headerLayout)
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)
        waveGestureView = findViewById(R.id.waveGestureView)
        searchEditText = findViewById(R.id.searchEditText)
        searchPanel = findViewById(R.id.searchPanel)
        searchScrim = findViewById(R.id.searchScrim)
        historyScroll = findViewById(R.id.historyScroll)
        txtNoResults = findViewById(R.id.txtNoResults)
        txtClock = findViewById(R.id.txtClock)
        txtDate = findViewById(R.id.txtDate)
        txtWeather = findViewById(R.id.txtWeather)
        txtLetterPopup = findViewById(R.id.txtLetterPopup)
        statusInfoRow = findViewById(R.id.statusInfoRow)
        txtNotificationSummary = findViewById(R.id.txtNotificationCount)
        txtBattery = findViewById(R.id.txtBattery)

        setupWindowInsets()

        // Initialize Database, Repository & Preferences
        db = AppDatabase.getDatabase(this)
        repository = AppRepository(this, db.appDao())
        prefs = SlimPreferences(this)

        // Set up home RecyclerView (Favorites + alphabetical browsing)
        adapter = AppListAdapter(this, emptyList()) { appItem, isLongClick ->
            if (isLongClick) {
                showAppOptionsDialog(appItem)
            } else {
                onAppClicked(appItem, fromSearch = false)
            }
        }
        appRecyclerView.layoutManager = LinearLayoutManager(this)
        appRecyclerView.adapter = adapter

        // Set up search results RecyclerView (inside the floating search panel)
        searchAdapter = AppListAdapter(this, emptyList()) { appItem, isLongClick ->
            if (isLongClick) {
                showAppOptionsDialog(appItem)
            } else {
                onAppClicked(appItem, fromSearch = true)
            }
        }
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        searchResultsRecyclerView.adapter = searchAdapter

        // Bind Custom Listeners
        waveGestureView.listener = this
        NotificationRegistry.registerListener(this)

        // The weather chip is purely informational — tapping it does nothing.
        // (It used to jump into Settings, which was an easy mis-tap.)

        // Tapping the header notification bell opens the system shade
        txtNotificationSummary.setOnClickListener {
            expandNotificationShade()
        }

        // Search functional trigger
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup Swipe-Up and swipe-left/right GestureDetector
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // Use tracked down position as fallback when e1 is null
                // (can happen during RecyclerView layout passes)
                val downX = e1?.x ?: lastDownX
                val downY = e1?.y ?: lastDownY
                val xDiff = e2.x - downX
                val yDiff = downY - e2.y

                // Swipe up opens search — only from the home (favorites) state,
                // never while browsing the alphabetical list or scrubbing.
                if (yDiff > 120f && Math.abs(velocityY) > 80f) {
                    if (prefs.swipeUpForSearch && !isAlphabetScrubbing) {
                        showSearchBar()
                        return true
                    }
                }
                // Swipe down opens the system notification shade
                if (yDiff < -120f && Math.abs(velocityY) > 80f) {
                    if (prefs.swipeDownForNotifications && !isAlphabetScrubbing &&
                        searchPanel.visibility != View.VISIBLE
                    ) {
                        expandNotificationShade()
                        return true
                    }
                }
                // Horizontal swipes exit alphabet apps view back to favorites list
                if (Math.abs(xDiff) > 120f && Math.abs(velocityX) > 80f) {
                    if (isAlphabetScrubbing) {
                        exitAlphabetMode()
                        return true
                    }
                }
                return false
            }
        })

        // Clock & Weather Update Loops
        startClockUpdates()

        // Load Apps and start Flow Collection
        lifecycleScope.launch {
            repository.refreshApps()

            // Observe cached apps (sorted by display name so renames re-sort correctly)
            launch {
                repository.allAppsFlow.collectLatest { apps ->
                    allApps = apps.sortedBy { it.displayLabel.lowercase(Locale.getDefault()) }
                    updateAlphabetLetters()
                    updateAdapterData()
                }
            }

            // Observe favorites
            launch {
                repository.favoritesFlow.collectLatest { favs ->
                    favorites = favs
                    updateAdapterData()
                }
            }
        }

        // Register for package install/remove broadcasts so newly installed
        // apps appear immediately without waiting for the next onResume cycle.
        val receiver = PackageChangeReceiver { scheduleAppRefresh() }
        packageChangeReceiver = receiver
        registerReceiver(receiver, PackageChangeReceiver.createIntentFilter())
    }

    /**
     * Draws the launcher edge-to-edge so our window background owns the entire
     * screen — crucially the status-bar strip. Without this, immersive mode
     * hides the status bar but the window doesn't extend into that area, so the
     * wallpaper shows through as a bright empty band above the (black) content.
     *
     * To keep the layout correct in both modes we re-apply the live system-bar
     * insets as padding: the header clears the status bar when it's visible and
     * the app list clears the navigation bar. When immersive hides the status
     * bar its top inset becomes 0, so the content simply fills the freed strip.
     */
    private fun setupWindowInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Pre-R: keep the legacy fullscreen flags (set in applyImmersiveMode)
            // and a safe gesture-zone fallback.
            systemGestureHeight = (64 * resources.displayMetrics.density).toInt()
            return
        }
        window.setDecorFitsSystemWindows(false)
        val baseHeaderTop = headerLayout.paddingTop
        val baseListBottom = appRecyclerView.paddingBottom
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            val gestureInsets = insets.getInsets(android.view.WindowInsets.Type.systemGestures())
            // Add a small buffer so even near-miss swipes don't fight the system.
            systemGestureHeight = (gestureInsets.bottom * 1.3f).toInt()
            if (systemGestureHeight == 0) {
                systemGestureHeight = (64 * resources.displayMetrics.density).toInt()
            }
            headerLayout.setPadding(
                headerLayout.paddingLeft, baseHeaderTop + bars.top,
                headerLayout.paddingRight, headerLayout.paddingBottom
            )
            appRecyclerView.setPadding(
                appRecyclerView.paddingLeft, appRecyclerView.paddingTop,
                appRecyclerView.paddingRight, baseListBottom + bars.bottom
            )
            view.onApplyWindowInsets(insets)
        }
    }

    /**
     * Re-assert immersive mode whenever we regain focus. The system tends to
     * restore the status bar after dialogs, the notification shade, app
     * switches, etc., so a launcher has to re-hide it on focus or it silently
     * creeps back — which made immersive mode feel fragile.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::prefs.isInitialized && prefs.immersiveMode &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
        }
    }

    /**
     * If Slim isn't the default Home app (e.g. right after an install, which
     * wipes the assignment), show the one-tap RoleManager dialog to reclaim it.
     * Fires at most once per process so a declined prompt doesn't keep popping.
     */
    private fun maybePromptDefaultLauncher() {
        if (defaultHomePrompted) return
        if (DefaultLauncherHelper.isDefaultHome(this)) return
        // Auto-prompt only where the one-tap dialog exists (Q+); on older
        // versions silently dumping the user into Home settings would be jarring
        // — the Settings button still covers them.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        defaultHomePrompted = true
        try {
            defaultHomeLauncher.launch(DefaultLauncherHelper.requestIntent(this))
        } catch (e: Exception) {
            // Role unavailable on this device — Settings button remains the path
        }
    }

    override fun onResume() {
        super.onResume()
        maybePromptDefaultLauncher()
        applyHeaderPreferences()
        applyAdaptiveColors()
        applyBackgroundMode()
        applyImmersiveMode()
        // Appearance / weather settings may have changed in Settings
        adapter.setShowIcons(prefs.showAppIcons)
        searchAdapter.setShowIcons(prefs.showAppIcons)
        updateWeather()
        // Pick up newly installed/removed apps — with retry for timing (F-Droid etc.)
        scheduleAppRefresh()

        // Battery level receiver for real-time updates
        if (prefs.immersiveMode) {
            if (batteryReceiver == null) {
                batteryReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        updateBatteryLevel()
                    }
                }
            }
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
    }

    /**
     * Refreshes the app list now, then retries after 800ms and 2500ms.
     * Some installers (F-Droid, Revanced Manager) finish writing the APK
     * before the system registers the launcher activity — the retries
     * catch apps that weren't visible yet on the first attempt.
     * Retries fire ONCE each (no infinite loop) to avoid spamming
     * notifyDataSetChanged and breaking swipe gestures.
     */
    private fun scheduleAppRefresh() {
        // Cancel any pending retries (call-site may have changed)
        handler.removeCallbacks(refreshRetry1)
        handler.removeCallbacks(refreshRetry2)
        refreshRetryCount = 0

        lifecycleScope.launch {
            repository.refreshApps()
        }
        // Retry after 800ms and 2500ms — each fires exactly once
        handler.postDelayed(refreshRetry1, 800)
        handler.postDelayed(refreshRetry2, 2500)
    }

    // Unified app click handling for both the home list and search results
    private fun onAppClicked(appItem: AppItem, fromSearch: Boolean) {
        // Tapping Slim's own entry opens Slim Settings instead of relaunching the launcher
        if (appItem.packageName == packageName) {
            hideSearchBar()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        if (fromSearch) {
            prefs.addToSearchHistory(appItem.id)
        }
        lifecycleScope.launch {
            repository.recordAppLaunch(appItem.id)
        }
        repository.launchApp(appItem)

        // Return home layout to Favorites immediately on launch
        exitAlphabetMode()
        hideSearchBar()
    }

    // ---- Header: clock, date, weather ----

    private fun applyHeaderPreferences() {
        txtClock.visibility = if (prefs.showClock) View.VISIBLE else View.GONE
        txtDate.visibility = if (prefs.showDate) View.VISIBLE else View.GONE
        txtWeather.visibility =
            if (prefs.weatherMode == SlimPreferences.WEATHER_OFF) View.GONE else View.VISIBLE
    }

    private fun startClockUpdates() {
        clockTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                    val timePattern = if (prefs.use24HourFormat) "HH:mm" else "h:mm a"
                    val timeFormat = SimpleDateFormat(timePattern, Locale.getDefault())
                    txtClock.text = timeFormat.format(Date())
                    txtDate.text = dateFormat.format(Date())
                    updateWeather()
                }
            }
        }, 0, 1000)
    }

    private fun updateWeather() {
        when (prefs.weatherMode) {
            SlimPreferences.WEATHER_OFF -> {
                txtWeather.visibility = View.GONE
            }
            SlimPreferences.WEATHER_REAL -> {
                txtWeather.visibility = View.VISIBLE
                val cached = prefs.lastWeatherText
                val cityConfigured = !prefs.weatherLatitude.isNaN() && !prefs.weatherLongitude.isNaN()
                // Only prompt "Set city" when no city is actually configured.
                // When a city is set but the forecast hasn't arrived yet (pending
                // or a transient fetch failure), show the city name instead of
                // misleadingly telling the user to set a city they already set.
                txtWeather.text = when {
                    cached.isNotEmpty() -> cached
                    cityConfigured -> "📍 ${prefs.weatherCity}"
                    else -> "📍 Set city"
                }
                maybeRefreshRealWeather()
            }
            else -> {
                // Ambient (offline) mode: a soft seasonal estimate, clearly not real data
                txtWeather.visibility = View.VISIBLE
                txtWeather.text = simulatedWeather()
            }
        }
    }

    // Dynamic, Offline-First Soft Weather Generator
    private fun simulatedWeather(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val month = calendar.get(Calendar.MONTH)

        val (emoji, temp) = when (month) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> { // Winter
                if (hour in 7..18) Pair("☁️", "1°C") else Pair("❄️", "-2°C")
            }
            in Calendar.MARCH..Calendar.MAY -> { // Spring
                if (hour in 7..18) Pair("⛅", "13°C") else Pair("🌙", "6°C")
            }
            in Calendar.JUNE..Calendar.AUGUST -> { // Summer
                if (hour in 7..18) Pair("☀️", "22°C") else Pair("🌙", "15°C")
            }
            else -> { // Autumn
                if (hour in 7..18) Pair("🍂", "10°C") else Pair("☁️", "5°C")
            }
        }
        return "$emoji $temp"
    }

    private fun maybeRefreshRealWeather() {
        if (weatherFetchInProgress) return
        val lat = prefs.weatherLatitude
        val lon = prefs.weatherLongitude
        if (lat.isNaN() || lon.isNaN()) return // No city configured yet

        val now = System.currentTimeMillis()
        // Skip if we already have weather that's still fresh.
        val haveFreshCache = prefs.lastWeatherText.isNotEmpty() &&
            (now - prefs.lastWeatherFetchTime) < WeatherService.REFRESH_INTERVAL_MS
        if (haveFreshCache) return
        // Throttle attempts (successes and failures alike) so the per-second
        // clock tick can't spin up back-to-back requests.
        if (now - lastWeatherAttemptTime < WEATHER_RETRY_INTERVAL_MS) return
        lastWeatherAttemptTime = now

        weatherFetchInProgress = true
        lifecycleScope.launch {
            val weather = weatherService.fetchCurrentWeather(lat, lon, prefs.useFahrenheit)
            if (weather != null) {
                val unit = if (prefs.useFahrenheit) "°F" else "°C"
                val text = "${weather.emoji()} ${weather.temperature.roundToInt()}$unit · ${weather.description()}"
                prefs.lastWeatherText = text
                prefs.lastWeatherFetchTime = System.currentTimeMillis()
                txtWeather.text = text
            }
            weatherFetchInProgress = false
        }
    }

    /**
     * Expands the system notification shade. Uses the StatusBarManager
     * reflection approach common to FOSS launchers; silently no-ops on
     * devices/versions where the hidden API is blocked.
     */
    @android.annotation.SuppressLint("WrongConstant", "PrivateApi")
    private fun expandNotificationShade() {
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
        } catch (e: Exception) {
            // Hidden API unavailable on this device — gesture does nothing
        }
    }

    // ---- Adaptive colors (readable on any wallpaper) ----

    private fun applyAdaptiveColors() {
        var useDarkText = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val wallpaperColors = WallpaperManager.getInstance(this)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                if (wallpaperColors != null) {
                    useDarkText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (wallpaperColors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                    } else {
                        ColorUtils.calculateLuminance(wallpaperColors.primaryColor.toArgb()) > 0.5
                    }
                }
            } catch (e: Exception) {
                // Keep the dark-wallpaper (light text) defaults
            }
        }

        val primary = getColor(if (useDarkText) R.color.text_primary_on_light else R.color.text_primary)
        val secondary = getColor(if (useDarkText) R.color.text_secondary_on_light else R.color.text_secondary)
        val muted = getColor(if (useDarkText) R.color.text_muted_on_light else R.color.text_muted)
        val accent = resolveAccentColor()

        txtClock.setTextColor(primary)
        txtDate.setTextColor(secondary)
        txtWeather.setTextColor(accent)
        waveGestureView.setPalette(primary, muted)
        // Home list sits directly on the wallpaper → adaptive text.
        adapter.setTextColors(primary, secondary, accent)
        // Search results sit on the dark floating panel → always light text.
        searchAdapter.setTextColors(
            getColor(R.color.text_primary),
            getColor(R.color.text_secondary),
            accent
        )
    }

    /** Material You dynamic accent on Android 12+, indigo fallback below. */
    private fun resolveAccentColor(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getColor(android.R.color.system_accent1_200)
        } else {
            getColor(R.color.accent_indigo)
        }
    }

    // ---- Touch & gestures ----

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            lastDownX = ev.getX(0)
            lastDownY = ev.getY(0)
            touchStartedOnWave = ev.x >= waveGestureView.left &&
                ev.y >= waveGestureView.top && ev.y <= waveGestureView.bottom
            resetInactivityTimer()
        }

        // Reliable swipe-up: pure distance check, no velocity gate.
        // Uses actual system gesture nav height so we never fight Android's
        // home/recent-apps gestures. Swipes from the middle of the screen always work.
        if (ev.action == MotionEvent.ACTION_UP && !touchStartedOnWave) {
            val inSystemGestureZone = lastDownY > resources.displayMetrics.heightPixels - systemGestureHeight
            val dy = lastDownY - ev.getY(0)
            if (!inSystemGestureZone && dy > 60f && prefs.swipeUpForSearch && !isAlphabetScrubbing
                && searchPanel.visibility != View.VISIBLE) {
                showSearchBar()
            }
        }

        // Still pass events to GestureDetector for swipe-down (notifications)
        // and horizontal swipe (exit alphabet mode).
        if (!touchStartedOnWave) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun resetInactivityTimer() {
        handler.removeCallbacks(returnToFavoritesRunnable)
        if (isAlphabetScrubbing && searchEditText.text.isEmpty()) {
            // Auto return to favorites only after 6 seconds of zero touch activity
            handler.postDelayed(returnToFavoritesRunnable, 6000)
        }
    }

    private fun exitAlphabetMode() {
        handler.removeCallbacks(returnToFavoritesRunnable)
        isAlphabetScrubbing = false
        updateAdapterData()
        appRecyclerView.scrollToPosition(0)
    }

    // ---- Search panel ----

    private fun showSearchBar() {
        if (searchPanel.visibility == View.GONE) {
            // Scrim fade-in
            searchScrim.alpha = 0f
            searchScrim.visibility = View.VISIBLE
            searchScrim.animate().alpha(1f).setDuration(220).start()
            // Panel scale in
            searchPanel.scaleX = 0.9f
            searchPanel.scaleY = 0.9f
            searchPanel.alpha = 0f
            searchPanel.visibility = View.VISIBLE
            searchPanel.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(280)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                .start()
            showRecentApps()
            searchEditText.requestFocus()
            handler.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
            }, 150)
        }
    }

    private fun hideSearchBar() {
        if (searchPanel.visibility == View.VISIBLE) {
            searchScrim.animate().alpha(0f).setDuration(180)
                .withEndAction { searchScrim.visibility = View.GONE }.start()
            searchPanel.animate()
                .scaleX(0.92f).scaleY(0.92f).alpha(0f)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction { searchPanel.visibility = View.GONE }
                .start()
            searchEditText.setText("")
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        }
    }

    /**
     * Shows recently searched apps as a vertical list (with icons, via the
     * search adapter) when the query is empty — icons make a recently used
     * app far quicker to spot than text-only chips did.
     */
    private fun showRecentApps() {
        // The old horizontal chip strip is retired; keep it hidden.
        historyScroll.visibility = View.GONE
        txtNoResults.visibility = View.GONE

        if (!prefs.searchHistoryEnabled) {
            searchAdapter.updateItems(emptyList())
            return
        }
        val historyApps = prefs.getSearchHistory()
            .mapNotNull { id -> allApps.find { it.id == id } }
        if (historyApps.isEmpty()) {
            searchAdapter.updateItems(emptyList())
            return
        }

        val items = mutableListOf<AdapterItem>()
        items.add(AdapterItem(ViewType.HEADER, headerText = getString(R.string.title_recent_searches)))
        for (app in historyApps) {
            items.add(AdapterItem(ViewType.APP, appItem = app))
        }
        searchAdapter.updateItems(items)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (searchPanel.visibility == View.VISIBLE) {
            hideSearchBar()
        } else if (isAlphabetScrubbing) {
            exitAlphabetMode()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clockTimer.cancel()
        handler.removeCallbacks(returnToFavoritesRunnable)
        handler.removeCallbacks(refreshRetry1)
        handler.removeCallbacks(refreshRetry2)
        NotificationRegistry.unregisterListener()
        packageChangeReceiver?.let { unregisterReceiver(it) }
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
    }

    /**
     * Applies background mode at the WINDOW level — not with a hacky overlay View.
     * The window background is drawn behind all content and stays fixed during
     * transitions, unlike a content View which moves with the activity animation.
     */
    private fun applyBackgroundMode() {
        when (prefs.backgroundMode) {
            SlimPreferences.BG_TRANSPARENT -> {
                // Wallpaper fully visible — window background is transparent
                setShowWallpaper(true)
                window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            SlimPreferences.BG_SOLID_BLACK -> {
                // Opaque black at window level — wallpaper is completely covered,
                // stays rock-solid during all transitions and gestures.
                // Drop FLAG_SHOW_WALLPAPER too: otherwise the system keeps
                // compositing the wallpaper in any area the window background
                // doesn't paint — most visibly the status-bar strip once
                // immersive mode hides the bar, leaving a bright empty band.
                setShowWallpaper(false)
                window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
                window.statusBarColor = android.graphics.Color.BLACK
                window.navigationBarColor = android.graphics.Color.BLACK
            }
            else -> { // BG_DIMMED
                // Semi-transparent dark tint drawn between wallpaper and content.
                // 18% black — strong enough for readability on bright wallpapers
                // without hiding the wallpaper entirely.
                setShowWallpaper(true)
                window.setBackgroundDrawable(ColorDrawable(0x2E000000))
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
        }
    }

    /** Toggles the window's wallpaper flag so opaque modes don't leak wallpaper. */
    private fun setShowWallpaper(show: Boolean) {
        if (show) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        }
    }

    /** Hides system status bar and shows launcher-native status info in header. */
    private fun applyImmersiveMode() {
        if (prefs.immersiveMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
            statusInfoRow.visibility = View.VISIBLE
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(android.view.WindowInsets.Type.statusBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
            }
            statusInfoRow.visibility = View.GONE
        }
        updateNotificationSummary()
        updateBatteryLevel()
    }

    /**
     * Updates the header notification chip (immersive mode only). Shows a bell
     * with the active-notification count, and hides entirely at zero so an
     * empty "0" never lingers as visual noise.
     */
    private fun updateNotificationSummary() {
        val count = NotificationRegistry.getNotificationCount()
        if (count > 0) {
            txtNotificationSummary.visibility = View.VISIBLE
            txtNotificationSummary.text = "🔔 $count"
        } else {
            txtNotificationSummary.visibility = View.GONE
        }
    }

    /** Reads current battery level and shows it in the header. */
    private fun updateBatteryLevel() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (scale > 0) (level * 100 / scale) else -1
        txtBattery.text = if (pct >= 0) "🔋 $pct%" else "🔋 --%"
    }

    override fun onNotificationsChanged() {
        runOnUiThread {
            updateAdapterData()
            if (prefs.immersiveMode) updateNotificationSummary()
        }
    }

    // ---- Wave gesture (alphabet index) ----

    /** Keeps the alphabet index compact: only letters that actually have apps. */
    private fun updateAlphabetLetters() {
        if (allApps.isEmpty()) return
        val letters = allApps
            .map { it.displayLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
            .distinct()
        // The settings gear used to live here, but sitting right after the last
        // letter it was easy to mis-tap on scrub-release. Settings is still
        // reachable via the row at the end of the all-apps list and the Slim
        // entry — so the index stays letters-only.
        waveGestureView.setLetters(letters)
    }

    override fun onLetterSelected(letter: String) {
        if (!isAlphabetScrubbing) {
            isAlphabetScrubbing = true
            updateAdapterData()
        }
        resetInactivityTimer()

        txtLetterPopup.visibility = View.VISIBLE
        txtLetterPopup.text = letter

        val targetIndex = filteredList.indexOfFirst { item ->
            item.type == ViewType.HEADER && item.headerText.equals(letter, ignoreCase = true)
        }
        if (targetIndex != -1) {
            (appRecyclerView.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(targetIndex, 0)
        }
    }

    override fun onLetterReleased() {
        txtLetterPopup.visibility = View.GONE
        resetInactivityTimer()
    }

    // ---- Search filtering & ranking ----

    /**
     * Matches any part of the app name, ranked for relevance:
     * 1. names starting with the query
     * 2. names with a word starting with the query
     * 3. names containing the query anywhere
     */
    private fun filterApps(query: String) {
        if (query.isEmpty()) {
            showRecentApps()
            return
        }
        historyScroll.visibility = View.GONE

        val q = query.lowercase(Locale.getDefault())
        val ranked = allApps.mapNotNull { app ->
            val label = app.displayLabel.lowercase(Locale.getDefault())
            val rank = when {
                label.startsWith(q) -> 0
                label.split(' ', '-', '_', '.').any { it.startsWith(q) } -> 1
                label.contains(q) -> 2
                else -> return@mapNotNull null
            }
            Pair(rank, app)
        }
            .sortedWith(compareBy({ it.first }, { it.second.displayLabel.lowercase(Locale.getDefault()) }))
            .map { it.second }

        val items = ranked.map { AdapterItem(ViewType.APP, appItem = it) }
        searchAdapter.updateItems(items)
        txtNoResults.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    // Merge Favorites and All Apps into custom RecyclerView elements
    private fun updateAdapterData() {
        val items = mutableListOf<AdapterItem>()

        // 1. Add Favorites section (Limited to top 5 manually selected OR dynamically tracked favorites)
        var favList = favorites.take(5)
        if (favList.isEmpty() && allApps.isNotEmpty()) {
            favList = allApps.sortedByDescending { it.launchCount }.take(5).filter { it.launchCount > 0 }
        }

        if (favList.isNotEmpty()) {
            items.add(AdapterItem(ViewType.HEADER, headerText = getString(R.string.title_favorites)))
            for (fav in favList) {
                items.add(AdapterItem(ViewType.APP, appItem = fav))
            }
        }

        // 1b. Active: apps with a live notification that aren't already a favorite.
        // This temporary section surfaces apps like Gmail the moment something
        // arrives and disappears again when the notification is cleared — so they
        // no longer hide until you scrub or search. Home view only; the full
        // alphabetical list below is already exhaustive while scrubbing.
        if (!isAlphabetScrubbing && allApps.isNotEmpty()) {
            val favIds = favList.map { it.id }.toSet()
            val activePackages = NotificationRegistry.getActivePackages()
            val activeApps = allApps.filter {
                it.packageName in activePackages && it.id !in favIds
            }
            if (activeApps.isNotEmpty()) {
                items.add(AdapterItem(ViewType.HEADER, headerText = getString(R.string.title_active)))
                for (app in activeApps) {
                    items.add(AdapterItem(ViewType.APP, appItem = app, forceNotificationPreview = true))
                }
            }
        }

        // 2. Add All Apps section ONLY when alphabet scrubbing is active
        if (isAlphabetScrubbing && allApps.isNotEmpty()) {
            items.add(AdapterItem(ViewType.HEADER, headerText = getString(R.string.title_all_apps)))
            var currentHeader = ""
            for (app in allApps) {
                val firstChar = app.displayLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                if (firstChar != currentHeader) {
                    currentHeader = firstChar
                    items.add(AdapterItem(ViewType.HEADER, headerText = currentHeader))
                }
                items.add(AdapterItem(ViewType.APP, appItem = app))
            }
            // Settings shortcut at the very end of the alphabetical list
            items.add(AdapterItem(ViewType.SETTINGS, headerText = SETTINGS_LETTER))
        }

        filteredList = items
        adapter.updateItems(filteredList)
    }

    private fun showAppOptionsDialog(appItem: AppItem) {
        // Long-pressing Slim's own entry also leads to Settings
        if (appItem.packageName == packageName) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        val favoriteOption = if (appItem.isFavorite) {
            getString(R.string.option_remove_favorite)
        } else {
            getString(R.string.option_add_favorite)
        }
        val options = arrayOf(
            favoriteOption,
            getString(R.string.option_rename),
            getString(R.string.option_hide),
            getString(R.string.option_app_info)
        )

        AlertDialog.Builder(this)
            .setTitle(appItem.displayLabel)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> lifecycleScope.launch {
                        repository.setAppAsFavorite(appItem.id, !appItem.isFavorite)
                    }
                    1 -> showRenameDialog(appItem)
                    2 -> lifecycleScope.launch {
                        repository.setAppHidden(appItem.id, true)
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.app_hidden_toast, appItem.displayLabel),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    3 -> openAppInfo(appItem.packageName)
                }
            }
            .show()
    }

    /** Opens the system App Info screen for force-stop, uninstall, permissions, etc. */
    private fun openAppInfo(packageName: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.app_info_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Rename dialog: empty input restores the app's original name. */
    private fun showRenameDialog(appItem: AppItem) {
        val input = EditText(this).apply {
            setText(appItem.displayLabel)
            setSelectAllOnFocus(true)
            hint = appItem.label
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.option_rename))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newLabel = input.text.toString().trim()
                lifecycleScope.launch {
                    repository.setCustomLabel(
                        appItem.id,
                        if (newLabel.isEmpty() || newLabel == appItem.label) null else newLabel
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    enum class ViewType { HEADER, APP, SETTINGS }
    data class AdapterItem(
        val type: ViewType,
        val headerText: String = "",
        val appItem: AppItem? = null,
        // Force the notification preview line visible even for non-favorites
        // (used by the home "Active" section).
        val forceNotificationPreview: Boolean = false
    )

    class AppListAdapter(
        private val context: Context,
        private var items: List<AdapterItem>,
        private val clickListener: (AppItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val pm: PackageManager = context.packageManager
        private val userManager =
            context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        private val iconCache = mutableMapOf<String, Drawable>()

        // Adaptive palette (kept readable on any wallpaper)
        private var primaryTextColor = context.getColor(R.color.text_primary)
        private var secondaryTextColor = context.getColor(R.color.text_secondary)
        private var accentColor = context.getColor(R.color.accent_indigo)

        // Text-only mode (Settings > Appearance)
        private var showIcons = true

        fun updateItems(newItems: List<AdapterItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun setTextColors(primary: Int, secondary: Int, accent: Int) {
            if (primary == primaryTextColor && secondary == secondaryTextColor && accent == accentColor) return
            primaryTextColor = primary
            secondaryTextColor = secondary
            accentColor = accent
            notifyDataSetChanged()
        }

        fun setShowIcons(show: Boolean) {
            if (show == showIcons) return
            showIcons = show
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return items[position].type.ordinal
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                ViewType.HEADER.ordinal -> {
                    val view = inflater.inflate(android.R.layout.simple_list_item_1, parent, false)
                    val textView = view.findViewById<TextView>(android.R.id.text1)
                    textView.textSize = 11f
                    textView.setPadding(16, 28, 16, 6)
                    textView.setAllCaps(true)
                    textView.letterSpacing = 0.06f
                    HeaderViewHolder(view)
                }
                ViewType.SETTINGS.ordinal -> {
                    val view = inflater.inflate(R.layout.item_app, parent, false)
                    SettingsViewHolder(view)
                }
                else -> {
                    val view = inflater.inflate(R.layout.item_app, parent, false)
                    AppViewHolder(view)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (holder is HeaderViewHolder) {
                val textView = holder.itemView.findViewById<TextView>(android.R.id.text1)
                textView.text = item.headerText
                textView.setTextColor(accentColor)
            } else if (holder is AppViewHolder && item.appItem != null) {
                val app = item.appItem
                holder.appName.text = app.displayLabel
                holder.appName.setTextColor(primaryTextColor)

                // Work profile badge
                holder.workBadge.visibility = if (app.isWorkProfile) View.VISIBLE else View.GONE

                // Text-only mode hides icons entirely
                if (!showIcons) {
                    holder.appIcon.visibility = View.GONE
                } else {
                    holder.appIcon.visibility = View.VISIBLE
                    val icon = iconCache[app.id] ?: try {
                        var resolvedIcon = pm.getApplicationIcon(app.packageName)
                        if (app.isWorkProfile) {
                            val user = userManager.getUserForSerialNumber(app.userSerial)
                            if (user != null) {
                                resolvedIcon = pm.getUserBadgedIcon(resolvedIcon, user)
                            }
                        }
                        iconCache[app.id] = resolvedIcon
                        resolvedIcon
                    } catch (e: Exception) {
                        context.getDrawable(android.R.drawable.sym_def_app_icon)
                    }
                    holder.appIcon.setImageDrawable(icon)
                }

                val media = NotificationRegistry.getMedia(app.packageName)
                val preview = NotificationRegistry.getNotificationPreview(app.packageName)
                when {
                    media != null -> {
                        // Now-playing row: album art (when available) replaces the
                        // app icon, with "Title — Artist" and a ♪ badge. The icon
                        // block above already set the real app icon, so recycled
                        // rows never keep stale art.
                        if (showIcons && media.art != null) {
                            holder.appIcon.visibility = View.VISIBLE
                            holder.appIcon.setImageBitmap(media.art)
                        }
                        if (app.isFavorite || item.forceNotificationPreview) {
                            holder.notificationPreview.visibility = View.VISIBLE
                            holder.notificationPreview.text =
                                if (!media.artist.isNullOrEmpty()) "♪ ${media.title} — ${media.artist}"
                                else "♪ ${media.title}"
                            holder.notificationPreview.setTextColor(secondaryTextColor)
                        } else {
                            holder.notificationPreview.visibility = View.GONE
                        }
                        holder.notificationCount.visibility = View.VISIBLE
                        holder.notificationCount.text = "♪"
                    }
                    preview != null -> {
                        // Show preview text for favorites and the Active section,
                        // badge-only for plain alphabetical rows.
                        if (app.isFavorite || item.forceNotificationPreview) {
                            holder.notificationPreview.visibility = View.VISIBLE
                            holder.notificationPreview.text = preview
                            holder.notificationPreview.setTextColor(secondaryTextColor)
                        } else {
                            holder.notificationPreview.visibility = View.GONE
                        }
                        holder.notificationCount.visibility = View.VISIBLE
                        holder.notificationCount.text = "!"
                    }
                    else -> {
                        holder.notificationPreview.visibility = View.GONE
                        holder.notificationCount.visibility = View.GONE
                    }
                }

                holder.itemView.setOnClickListener {
                    clickListener(app, false)
                }
                holder.itemView.setOnLongClickListener {
                    clickListener(app, true)
                    true
                }
            } else if (holder is SettingsViewHolder) {
                // Settings shortcut row
                holder.appIcon.visibility = View.GONE
                holder.appName.text = context.getString(R.string.settings_title)
                holder.appName.setTextColor(accentColor)
                holder.workBadge.visibility = View.GONE
                holder.notificationPreview.visibility = View.GONE
                holder.notificationCount.visibility = View.GONE
                holder.itemView.setOnClickListener {
                    val intent = Intent(context, SettingsActivity::class.java)
                    context.startActivity(intent)
                }
                holder.itemView.setOnLongClickListener(null)
            }
        }

        override fun getItemCount(): Int = items.size

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)
        class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appIcon: ImageView = view.findViewById(R.id.imgAppIcon)
            val appName: TextView = view.findViewById(R.id.txtAppName)
            val workBadge: TextView = view.findViewById(R.id.txtWorkBadge)
            val notificationPreview: TextView = view.findViewById(R.id.txtNotificationPreview)
            val notificationCount: TextView = view.findViewById(R.id.txtNotificationCount)
        }
        /** Reuses item_app layout but styled as a settings shortcut. */
        class SettingsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appIcon: ImageView = view.findViewById(R.id.imgAppIcon)
            val appName: TextView = view.findViewById(R.id.txtAppName)
            val workBadge: TextView = view.findViewById(R.id.txtWorkBadge)
            val notificationPreview: TextView = view.findViewById(R.id.txtNotificationPreview)
            val notificationCount: TextView = view.findViewById(R.id.txtNotificationCount)
        }
    }
}
