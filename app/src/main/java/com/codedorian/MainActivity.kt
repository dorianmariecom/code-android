package com.codedorian

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.hotwire.core.turbo.visit.VisitAction
import dev.hotwire.core.turbo.visit.VisitOptions
import dev.hotwire.core.turbo.visit.VisitProposal
import dev.hotwire.navigation.activities.HotwireActivity
import dev.hotwire.navigation.navigator.Navigator
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.navigator.NavigatorHost
import dev.hotwire.navigation.tabs.HotwireBottomNavigationController
import dev.hotwire.navigation.tabs.HotwireBottomTab
import dev.hotwire.navigation.util.applyDefaultImeWindowInsets
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : HotwireActivity() {
    private lateinit var bottomNavigationController: HotwireBottomNavigationController
    private var configurations = emptyList<NavigatorConfiguration>()
    private val locations = TabLocations(AppConfig.baseURL)
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private var scrollPositionsByLocation = mutableMapOf<String, Int>()
    private var programmaticSelection = false
    private var tabsReady = false
    private val completedTabs = mutableSetOf<Int>()
    private val pendingRoutes = mutableMapOf<Int, PendingRoute>()

    private data class PendingRoute(val location: String, val options: VisitOptions, val bundle: Bundle?)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Server-defined tabs are reconstructed from our cache, with new host IDs.
        // Persist selection/scroll ourselves instead of restoring obsolete fragment IDs.
        AppConfig.tabs = readCachedTabs() ?: listOf(Tab("loading…", "circle", ""))
        tabsReady = AppConfig.tabs.any { it.path.isNotBlank() }
        scrollPositionsByLocation = readScrollPositions()
        configurations = AppConfig.tabs.map { tab ->
            NavigatorConfiguration(
                name = tab.title,
                navigatorHostId = View.generateViewId(),
                startLocation = requireNotNull(locations.resolve(tab.path.ifBlank { "/" })),
            )
        }
        super.onCreate(null)
        setContentView(R.layout.activity_main)
        enableEdgeToEdge()
        findViewById<View>(R.id.main).applyDefaultImeWindowInsets()
        loadTabs()
        openIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openIntent(intent)
    }

    override fun navigatorConfigurations() = configurations

    private fun loadTabs() {
        val container = findViewById<FrameLayout>(R.id.navigator_container)
        val tabs = AppConfig.tabs
        val hosts = configurations.map { configuration ->
            container.addView(FragmentContainerView(this).apply {
                id = configuration.navigatorHostId
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            })
            NavigatorHost()
        }
        // Make the hosts available to Hotwire's controller without creating their views.
        // load() must mark them lazy before onViewCreated registers them with Hotwire.
        supportFragmentManager.beginTransaction().apply {
            hosts.forEachIndexed { index, host ->
                add(configurations[index].navigatorHostId, host, "tab_$index")
                setMaxLifecycle(host, Lifecycle.State.CREATED)
            }
        }.commitNow()

        val selectedIndex = intentLocation(intent)?.let(::tabIndexForLocation)?.takeIf { it >= 0 }
            ?: prefs.getString(KEY_LAST_TAB_PATH, null)?.let { path -> tabs.indexOfFirst { it.path == path } }?.takeIf { it >= 0 }
            ?: prefs.getInt(KEY_LAST_TAB_INDEX, -1).takeIf { it in tabs.indices }
            ?: tabs.indexOfFirst { it.default }.takeIf { it >= 0 }
            ?: 0
        bottomNavigationController = HotwireBottomNavigationController(
            activity = this,
            view = findViewById(R.id.bottom_navigation),
            clearNavigationOnTabReselection = false,
            lazyLoadTabs = true,
        )
        bottomNavigationController.load(tabs.mapIndexed { index, tab ->
            HotwireBottomTab(
                title = tab.title,
                iconResId = resources.getIdentifier("material_${tab.image}", "drawable", packageName)
                    .takeIf { it != 0 } ?: R.drawable.material_circle,
                configuration = configurations[index],
            )
        }, selectedIndex)
        rememberTab(selectedIndex)
        bottomNavigationController.setOnTabSelectedListener { index, _ ->
            rememberTab(index)
            if (!programmaticSelection) refreshTab(index)
        }
        bottomNavigationController.view.setOnItemReselectedListener { item ->
            if (!programmaticSelection) refreshTab(item.itemId)
        }
        supportFragmentManager.beginTransaction().apply {
            hosts.forEach { setMaxLifecycle(it, Lifecycle.State.RESUMED) }
        }.commitNow()
    }

    private fun rememberTab(index: Int) {
        if (!tabsReady) return
        prefs.edit().putInt(KEY_LAST_TAB_INDEX, index)
            .putString(KEY_LAST_TAB_PATH, AppConfig.tabs[index].path).apply()
    }

    private fun refreshTab(index: Int) {
        // First selection already performs a network visit through lazy loading.
        if (index !in completedTabs) return
        val navigator = delegate.currentNavigator ?: return
        val root = configurations[index].startLocation
        val destination = navigator.currentDestination
        if (destination is WebFragment && locations.key(destination.location) == locations.key(root)) {
            destination.reloadCurrentPage()
        } else {
            bottomNavigationController.route(root, VisitOptions(action = VisitAction.REPLACE))
        }
    }

    private fun intentLocation(intent: Intent?): String? {
        val path = intent?.getStringExtra("path")?.trim()?.takeIf { it.isNotEmpty() }
            ?: intent?.dataString ?: return null
        return locations.resolve(path)
    }

    private fun openIntent(intent: Intent?) {
        if (!tabsReady) return // The tab bridge recreates us once the server supplies tabs.
        val location = intentLocation(intent) ?: return
        routeToLocation(location)
        // Do not replay an already consumed notification on activity recreation.
        intent?.removeExtra("path")
        intent?.data = null
    }

    fun tabIndexForLocation(location: String): Int =
        if (tabsReady) locations.matchingIndex(location, AppConfig.tabs.map { it.path }) else -1

    fun routeToLocation(location: String, options: VisitOptions = VisitOptions(), bundle: Bundle? = null) {
        val index = tabIndexForLocation(location)
        programmaticSelection = true
        try {
            if (index >= 0) bottomNavigationController.selectTab(index)
        } finally {
            programmaticSelection = false
        }
        // A newly selected lazy host isn't ready yet. Keep the route until its
        // destination exists rather than losing the deep link to a null navigator.
        val hostId = bottomNavigationController.currentTab.configuration.navigatorHostId
        pendingRoutes[hostId] = PendingRoute(location, options, bundle)
        dispatchPendingRoute(hostId)
    }

    override fun onNavigatorReady(navigator: Navigator) {
        dispatchPendingRoute(navigator.configuration.navigatorHostId)
    }

    private fun dispatchPendingRoute(hostId: Int) {
        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            val host = supportFragmentManager.findFragmentById(hostId) as? NavigatorHost ?: return@post
            if (!host.isReady()) return@post
            val route = pendingRoutes.remove(hostId) ?: return@post
            host.navigator.route(route.location, route.options, route.bundle)
        }
    }

    fun shouldSwitchTab(proposal: VisitProposal, configuration: NavigatorConfiguration): Boolean {
        if (proposal.properties["context"] == "modal") return false
        val index = tabIndexForLocation(proposal.location)
        return index >= 0 && configurations[index].navigatorHostId != configuration.navigatorHostId
    }

    fun onPageCompleted(navigator: Navigator) {
        val index = configurations.indexOfFirst { it.navigatorHostId == navigator.configuration.navigatorHostId }
        if (index >= 0) completedTabs.add(index)
    }

    fun isNavigatorActive(navigator: Navigator): Boolean = delegate.currentNavigator == navigator

    fun persistCurrentTabScroll(scrollY: Int, location: String) {
        if (scrollY < 0) return
        scrollPositionsByLocation[locations.key(location)] = scrollY
        storeScrollPositions()
    }

    fun consumePendingScrollRestore(location: String): Int? = savedScrollForLocation(location)

    fun savedScrollForLocation(location: String): Int? = scrollPositionsByLocation[locations.key(location)]

    fun saveCachedTabs(tabs: List<Tab>) {
        val json = JSONArray()
        tabs.forEach { tab ->
            json.put(
                JSONObject().apply {
                    put("title", tab.title)
                    put("image", tab.image)
                    put("path", tab.path)
                    put("default", tab.default)
                },
            )
        }
        prefs.edit().putString(KEY_CACHED_TABS, json.toString()).apply()
    }

    private fun readCachedTabs(): List<Tab>? {
        val raw = prefs.getString(KEY_CACHED_TABS, null) ?: return null
        val json = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        val result = mutableListOf<Tab>()
        for (i in 0 until json.length()) {
            val obj = json.optJSONObject(i) ?: continue
            val title = obj.optString("title", "")
            val image = obj.optString("image", "")
            val path = obj.optString("path", "")
            val default = obj.optBoolean("default", false)
            if (title.isNotBlank()) result.add(Tab(title = title, image = image, path = path, default = default))
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun readScrollPositions(): MutableMap<String, Int> {
        val raw = prefs.getString(KEY_SCROLL_POSITIONS, null) ?: return mutableMapOf()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return mutableMapOf()
        val result = mutableMapOf<String, Int>()
        json.keys().forEach { key ->
            result[key] = json.optInt(key, 0)
        }
        return result
    }

    private fun storeScrollPositions() {
        val json = JSONObject()
        scrollPositionsByLocation.forEach { (location, scrollY) ->
            json.put(location, scrollY)
        }
        prefs.edit().putString(KEY_SCROLL_POSITIONS, json.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "app_state"
        private const val KEY_LAST_TAB_INDEX = "last_tab_index"
        private const val KEY_LAST_TAB_PATH = "last_tab_path"
        private const val KEY_CACHED_TABS = "cached_tabs"
        private const val KEY_SCROLL_POSITIONS = "scroll_positions"
    }
}
