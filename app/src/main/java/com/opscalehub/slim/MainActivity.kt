package com.opscalehub.slim

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), WaveGestureView.OnLetterSelectedListener, NotificationRegistry.NotificationUpdateListener {

    private lateinit var appRecyclerView: RecyclerView
    private lateinit var waveGestureView: WaveGestureView
    private lateinit var searchEditText: EditText
    private lateinit var txtClock: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtWeather: TextView
    private lateinit var txtLetterPopup: TextView
    private lateinit var txtSettingsTrigger: TextView

    private lateinit var db: AppDatabase
    private lateinit var repository: AppRepository
    private lateinit var adapter: AppListAdapter

    private var allApps = listOf<AppItem>()
    private var favorites = listOf<AppItem>()
    private var filteredList = listOf<AdapterItem>()
    
    private val clockTimer = Timer()
    private lateinit var gestureDetector: GestureDetector

    // State controlling list visibility
    private var isAlphabetScrubbing = false
    private val handler = Handler(Looper.getMainLooper())
    private val returnToFavoritesRunnable = Runnable {
        exitAlphabetMode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        appRecyclerView = findViewById(R.id.appRecyclerView)
        waveGestureView = findViewById(R.id.waveGestureView)
        searchEditText = findViewById(R.id.searchEditText)
        txtClock = findViewById(R.id.txtClock)
        txtDate = findViewById(R.id.txtDate)
        txtWeather = findViewById(R.id.txtWeather)
        txtLetterPopup = findViewById(R.id.txtLetterPopup)
        txtSettingsTrigger = findViewById(R.id.txtSettingsTrigger)

        // Initialize Database & Repository
        db = AppDatabase.getDatabase(this)
        repository = AppRepository(this, db.appDao())

        // Set up RecyclerView
        adapter = AppListAdapter(this, emptyList()) { appItem, isLongClick ->
            if (isLongClick) {
                showAppOptionsDialog(appItem)
            } else {
                lifecycleScope.launch {
                    repository.recordAppLaunch(appItem.packageName)
                }
                repository.launchApp(appItem)
                
                // Return home layout to Favorites immediately on launch
                exitAlphabetMode()
                hideSearchBar()
            }
        }
        appRecyclerView.layoutManager = LinearLayoutManager(this)
        appRecyclerView.adapter = adapter

        // Bind Custom Listeners
        waveGestureView.listener = this
        NotificationRegistry.registerListener(this)

        // Search functional trigger
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Settings Dialog Trigger
        txtSettingsTrigger.setOnClickListener {
            showSettingsDialog()
        }

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

                // Swipe up opens search
                if (yDiff > 120f && Math.abs(velocityY) > 80f) {
                    showSearchBar()
                    return true
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

        // Clock & Dynamic Weather Update Loops
        startClockUpdates()

        // Load Apps and start Flow Collection
        lifecycleScope.launch {
            repository.refreshApps()
            
            // Observe cached apps
            launch {
                repository.allAppsFlow.collectLatest { apps ->
                    allApps = apps
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

    private fun startClockUpdates() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        clockTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    txtClock.text = timeFormat.format(Date())
                    txtDate.text = dateFormat.format(Date())
                    updateWeather()
                }
            }
        }, 0, 1000)
    }

    // Dynamic, Offline-First Soft Weather Generator
    private fun updateWeather() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val month = calendar.get(Calendar.MONTH)
        
        val (emoji, temp) = when (month) {
            in Calendar.DECEMBER..Calendar.FEBRUARY -> { // Winter
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
        txtWeather.text = "$emoji $temp"
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Intercept touches to detect swipes and reset inactivity timer
        gestureDetector.onTouchEvent(ev)
        if (ev.action == MotionEvent.ACTION_DOWN) {
            resetInactivityTimer()
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
        Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
    }

    private fun showSearchBar() {
        val searchContainer = findViewById<View>(R.id.searchContainer)
        val scrim = findViewById<View>(R.id.searchScrim)
        if (searchContainer.visibility == View.GONE) {
            // Scrim fade-in
            scrim.alpha = 0f
            scrim.visibility = View.VISIBLE
            scrim.animate().alpha(1f).setDuration(220).start()
            // Search box scale in from center
            searchContainer.scaleX = 0.85f
            searchContainer.scaleY = 0.85f
            searchContainer.alpha = 0f
            searchContainer.visibility = View.VISIBLE
            searchContainer.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(280)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                .start()
            searchEditText.requestFocus()
            handler.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
            }, 150)
            isAlphabetScrubbing = true
            updateAdapterData()
        }
    }

    private fun hideSearchBar() {
        val searchContainer = findViewById<View>(R.id.searchContainer)
        val scrim = findViewById<View>(R.id.searchScrim)
        if (searchContainer.visibility == View.VISIBLE) {
            scrim.animate().alpha(0f).setDuration(180).withEndAction { scrim.visibility = View.GONE }.start()
            searchContainer.animate()
                .scaleX(0.88f).scaleY(0.88f).alpha(0f)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction { searchContainer.visibility = View.GONE }
                .start()
            searchEditText.setText("")
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
            exitAlphabetMode()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val searchContainer = findViewById<View>(R.id.searchContainer)
        if (searchContainer.visibility == View.VISIBLE) {
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

    // Wave Gesture Listeners (Enhanced dynamic apps listing)
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

    // Filter apps based on query
    private fun filterApps(query: String) {
        if (query.isEmpty()) {
            updateAdapterData()
            return
        }
        val lowercaseQuery = query.lowercase(Locale.getDefault())
        val matchingApps = allApps.filter { it.label.lowercase(Locale.getDefault()).contains(lowercaseQuery) }
        
        val items = mutableListOf<AdapterItem>()
        for (app in matchingApps) {
            items.add(AdapterItem(ViewType.APP, appItem = app))
        }
        filteredList = items
        adapter.updateItems(filteredList)
    }

    // Merge Favorites and All Apps into custom RecyclerView elements
    private fun updateAdapterData() {
        if (searchEditText.text.isNotEmpty()) return

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

        // 2. Add All Apps section ONLY when search or scrubbing is active
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

    private fun showSettingsDialog() {
        val options = arrayOf(
            getString(R.string.settings_choose_home),
            getString(R.string.settings_notification_permission)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                        startActivity(intent)
                    }
                    1 -> {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        startActivity(intent)
                        Toast.makeText(this, "Enable Slim Launcher in the list", Toast.LENGTH_LONG).show()
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

        fun updateItems(newItems: List<AdapterItem>) {
            items = newItems
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
                textView.setTextColor(context.getColor(R.color.accent_indigo))
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
            } else if (holder is AppViewHolder && item.appItem != null) {
                val app = item.appItem
                holder.appName.text = app.label

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
