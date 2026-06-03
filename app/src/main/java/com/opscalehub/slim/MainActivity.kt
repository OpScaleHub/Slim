package com.opscalehub.slim

import android.app.AlertDialog
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

    private lateinit var appRecyclerView: RecyclerView
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var waveGestureView: WaveGestureView
    private lateinit var searchEditText: EditText
    private lateinit var searchPanel: View
    private lateinit var searchScrim: View
    private lateinit var historyScroll: HorizontalScrollView
    private lateinit var historyChipsContainer: LinearLayout
    private lateinit var txtNoResults: TextView
    private lateinit var txtClock: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtWeather: TextView
    private lateinit var txtLetterPopup: TextView

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
    private var weatherFetchInProgress = false
    private val handler = Handler(Looper.getMainLooper())
    private val returnToFavoritesRunnable = Runnable {
        exitAlphabetMode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        appRecyclerView = findViewById(R.id.appRecyclerView)
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)
        waveGestureView = findViewById(R.id.waveGestureView)
        searchEditText = findViewById(R.id.searchEditText)
        searchPanel = findViewById(R.id.searchPanel)
        searchScrim = findViewById(R.id.searchScrim)
        historyScroll = findViewById(R.id.historyScroll)
        historyChipsContainer = findViewById(R.id.historyChipsContainer)
        txtNoResults = findViewById(R.id.txtNoResults)
        txtClock = findViewById(R.id.txtClock)
        txtDate = findViewById(R.id.txtDate)
        txtWeather = findViewById(R.id.txtWeather)
        txtLetterPopup = findViewById(R.id.txtLetterPopup)

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

        // Tapping the weather area opens Slim Settings (weather section)
        txtWeather.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
                val xDiff = e2.x - (e1?.x ?: 0f)
                val yDiff = (e1?.y ?: 0f) - e2.y

                // Swipe up opens search — only from the home (favorites) state,
                // never while browsing the alphabetical list or scrubbing.
                if (yDiff > 120f && Math.abs(velocityY) > 80f) {
                    if (prefs.swipeUpForSearch && !isAlphabetScrubbing) {
                        showSearchBar()
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

            // Observe cached apps
            launch {
                repository.allAppsFlow.collectLatest { apps ->
                    allApps = apps
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
    }

    override fun onResume() {
        super.onResume()
        applyHeaderPreferences()
        applyAdaptiveColors()
        // Weather mode may have changed in Settings
        updateWeather()
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
            prefs.addToSearchHistory(appItem.packageName)
        }
        lifecycleScope.launch {
            repository.recordAppLaunch(appItem.packageName)
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
                txtWeather.text = if (cached.isNotEmpty()) cached else "📍 Set city"
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
        val cacheAge = System.currentTimeMillis() - prefs.lastWeatherFetchTime
        if (cacheAge < WeatherService.REFRESH_INTERVAL_MS) return

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
            // Touches starting on the alphabet index belong to letter scrubbing —
            // they must never trigger the swipe-up search gesture.
            touchStartedOnWave = ev.x >= waveGestureView.left &&
                ev.y >= waveGestureView.top && ev.y <= waveGestureView.bottom
            resetInactivityTimer()
        }
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
            updateHistoryChips()
            searchAdapter.updateItems(emptyList())
            txtNoResults.visibility = View.GONE
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

    /** Shows recently searched apps as quick-launch chips above the results. */
    private fun updateHistoryChips() {
        historyChipsContainer.removeAllViews()
        if (!prefs.searchHistoryEnabled) {
            historyScroll.visibility = View.GONE
            return
        }
        val historyApps = prefs.getSearchHistory()
            .mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
        if (historyApps.isEmpty()) {
            historyScroll.visibility = View.GONE
            return
        }

        historyScroll.visibility = View.VISIBLE
        val density = resources.displayMetrics.density
        for (app in historyApps) {
            val chip = TextView(this).apply {
                text = app.label
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
                background = getDrawable(R.drawable.chip_bg)
                setPadding(
                    (14 * density).toInt(), (8 * density).toInt(),
                    (14 * density).toInt(), (8 * density).toInt()
                )
                setOnClickListener { onAppClicked(app, fromSearch = true) }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * density).toInt() }
            historyChipsContainer.addView(chip, params)
        }
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
        NotificationRegistry.unregisterListener()
    }

    override fun onNotificationsChanged() {
        runOnUiThread {
            updateAdapterData()
        }
    }

    // ---- Wave gesture (alphabet index) ----

    /** Keeps the alphabet index compact: only letters that actually have apps. */
    private fun updateAlphabetLetters() {
        if (allApps.isEmpty()) return
        val letters = allApps
            .map { it.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
            .distinct()
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
            searchAdapter.updateItems(emptyList())
            txtNoResults.visibility = View.GONE
            updateHistoryChips()
            return
        }
        historyScroll.visibility = View.GONE

        val q = query.lowercase(Locale.getDefault())
        val ranked = allApps.mapNotNull { app ->
            val label = app.label.lowercase(Locale.getDefault())
            val rank = when {
                label.startsWith(q) -> 0
                label.split(' ', '-', '_', '.').any { it.startsWith(q) } -> 1
                label.contains(q) -> 2
                else -> return@mapNotNull null
            }
            Pair(rank, app)
        }
            .sortedWith(compareBy({ it.first }, { it.second.label.lowercase(Locale.getDefault()) }))
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

        // 2. Add All Apps section ONLY when alphabet scrubbing is active
        if (isAlphabetScrubbing && allApps.isNotEmpty()) {
            items.add(AdapterItem(ViewType.HEADER, headerText = getString(R.string.title_all_apps)))
            var currentHeader = ""
            for (app in allApps) {
                val firstChar = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                if (firstChar != currentHeader) {
                    currentHeader = firstChar
                    items.add(AdapterItem(ViewType.HEADER, headerText = currentHeader))
                }
                items.add(AdapterItem(ViewType.APP, appItem = app))
            }
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

        val options = if (appItem.isFavorite) {
            arrayOf("Remove from Favorites")
        } else {
            arrayOf("Add to Favorites")
        }

        AlertDialog.Builder(this)
            .setTitle(appItem.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        lifecycleScope.launch {
                            repository.setAppAsFavorite(appItem.packageName, !appItem.isFavorite)
                        }
                    }
                }
            }
            .show()
    }

    enum class ViewType { HEADER, APP }
    data class AdapterItem(
        val type: ViewType,
        val headerText: String = "",
        val appItem: AppItem? = null
    )

    class AppListAdapter(
        private val context: Context,
        private var items: List<AdapterItem>,
        private val clickListener: (AppItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val pm: PackageManager = context.packageManager
        private val iconCache = mutableMapOf<String, Drawable>()

        // Adaptive palette (kept readable on any wallpaper)
        private var primaryTextColor = context.getColor(R.color.text_primary)
        private var secondaryTextColor = context.getColor(R.color.text_secondary)
        private var accentColor = context.getColor(R.color.accent_indigo)

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

        override fun getItemViewType(position: Int): Int {
            return items[position].type.ordinal
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == ViewType.HEADER.ordinal) {
                val view = inflater.inflate(android.R.layout.simple_list_item_1, parent, false)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.textSize = 13f
                textView.setPadding(0, 24, 0, 8)
                HeaderViewHolder(view)
            } else {
                val view = inflater.inflate(R.layout.item_app, parent, false)
                AppViewHolder(view)
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
                holder.appName.text = app.label
                holder.appName.setTextColor(primaryTextColor)

                val icon = iconCache[app.packageName] ?: try {
                    val resolvedIcon = pm.getApplicationIcon(app.packageName)
                    iconCache[app.packageName] = resolvedIcon
                    resolvedIcon
                } catch (e: Exception) {
                    context.getDrawable(android.R.drawable.sym_def_app_icon)
                }
                holder.appIcon.setImageDrawable(icon)

                val preview = NotificationRegistry.getNotificationPreview(app.packageName)
                if (preview != null && app.isFavorite) {
                    holder.notificationPreview.visibility = View.VISIBLE
                    holder.notificationPreview.text = preview
                    holder.notificationPreview.setTextColor(secondaryTextColor)
                    holder.notificationCount.visibility = View.VISIBLE
                    holder.notificationCount.text = "!"
                } else {
                    holder.notificationPreview.visibility = View.GONE
                    holder.notificationCount.visibility = View.GONE
                }

                holder.itemView.setOnClickListener {
                    clickListener(app, false)
                }
                holder.itemView.setOnLongClickListener {
                    clickListener(app, true)
                    true
                }
            }
        }

        override fun getItemCount(): Int = items.size

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)
        class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appIcon: ImageView = view.findViewById(R.id.imgAppIcon)
            val appName: TextView = view.findViewById(R.id.txtAppName)
            val notificationPreview: TextView = view.findViewById(R.id.txtNotificationPreview)
            val notificationCount: TextView = view.findViewById(R.id.txtNotificationCount)
        }
    }
}
