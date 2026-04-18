package com.codedorian

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.bottomnavigation.BottomNavigationView
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
    private var hotwireTabs: List<HotwireBottomTab> = emptyList()
    private var tabItemIds: List<Int> = emptyList()
    private var hotwireNavigatorConfigurations: MutableList<NavigatorConfiguration> =
        mutableListOf(
            NavigatorConfiguration(
                name = "loading…",
                navigatorHostId = R.id.navigator_host_loading,
                startLocation = AppConfig.baseURL,
            ),
        )
    private val pendingDeepLinks = ArrayDeque<String>()
    private var tabsReady = false
    private var isProgrammaticTabSelection = false
    private var scrollPositionsByLocation: MutableMap<String, Int> = mutableMapOf()
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        handleDeepLinkIntent(intent)
        AppConfig.tabs = readCachedTabs() ?: listOf(Tab(title = "loading…", image = "circle", path = ""))
        scrollPositionsByLocation = readScrollPositions()
        setContentView(R.layout.activity_main)
        enableEdgeToEdge()
        findViewById<View>(R.id.main).applyDefaultImeWindowInsets()
        tabsChanged()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
        maybeHandleInitialNavigation()
    }

    fun tabsChanged() {
        tabsReady = false

        val tabs = AppConfig.tabs
        val isPlaceholder = isPlaceholderTabs(tabs)
        val navigatorContainer = findViewById<FrameLayout>(R.id.navigator_container)
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        supportFragmentManager
            .beginTransaction()
            .apply {
                supportFragmentManager.fragments
                    .filterIsInstance<NavigatorHost>()
                    .forEach { remove(it) }
            }.commitNow()

        navigatorContainer.removeAllViews()
        bottomNavigationView.menu.clear()

        if (isPlaceholder) {
            buildLoadingTab(navigatorContainer)
        } else {
            buildConfiguredTabs(tabs, navigatorContainer)
        }

        val selectedTabIndex =
            if (tabsReady) {
                readLastTabIndex()?.takeIf { it in hotwireTabs.indices }
                    ?: tabs.indexOfFirst { it.default }.takeIf { it >= 0 }
                    ?: 0
            } else {
                0
            }

        bottomNavigationController = HotwireBottomNavigationController(this, bottomNavigationView)
        bottomNavigationController.load(hotwireTabs, selectedTabIndex = selectedTabIndex)
        tabItemIds = List(bottomNavigationView.menu.size()) { index -> bottomNavigationView.menu.getItem(index).itemId }
        if (tabsReady) {
            storeLastTabIndex(selectedTabIndex)
        }
        bottomNavigationController.setOnTabSelectedListener { index, _ ->
            if (!tabsReady) return@setOnTabSelectedListener
            storeLastTabIndex(index)
        }

        bottomNavigationView.setOnItemReselectedListener { item ->
            if (!tabsReady || isProgrammaticTabSelection) return@setOnItemReselectedListener
            val index = tabItemIds.indexOf(item.itemId).takeIf { it >= 0 } ?: return@setOnItemReselectedListener
            routeToTabRoot(index)
        }

        maybeHandleInitialNavigation()
    }

    override fun navigatorConfigurations() = hotwireNavigatorConfigurations

    fun persistLastTabLocation(
        location: String,
        navigator: Navigator,
    ) {}

    fun persistLastTabLocation(location: String) {}

    fun persistCurrentTabScroll(
        scrollY: Int,
        navigator: Navigator,
        location: String? = null,
    ) {
        if (!isNavigatorActive(navigator)) return
        persistCurrentTabScroll(scrollY, location)
    }

    fun persistCurrentTabScroll(
        scrollY: Int,
        location: String? = null,
    ) {
        if (scrollY < 0) return
        val normalizedLocation = location?.let(::canonicalLocationSafe) ?: return
        scrollPositionsByLocation[normalizedLocation] = scrollY
        storeScrollPositions()
    }

    fun consumePendingScrollRestore(navigator: Navigator): Int? = null

    fun consumePendingScrollRestore(location: String): Int? {
        val normalizedLocation = canonicalLocationSafe(location)
        return scrollPositionsByLocation[normalizedLocation]
    }

    fun isNavigatorActive(navigator: Navigator): Boolean = delegate.currentNavigator == navigator

    fun savedScrollForLocation(location: String): Int? {
        val normalizedLocation = canonicalLocationSafe(location)
        return scrollPositionsByLocation[normalizedLocation]
    }

    private fun buildLoadingTab(navigatorContainer: FrameLayout) {
        val containerView =
            FragmentContainerView(this).apply {
                id = R.id.navigator_host_loading
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
        navigatorContainer.addView(containerView)

        supportFragmentManager
            .beginTransaction()
            .add(R.id.navigator_host_loading, NavigatorHost(), "tab_loading")
            .commitNow()

        val configuration =
            NavigatorConfiguration(
                name = "loading…",
                navigatorHostId = R.id.navigator_host_loading,
                startLocation = AppConfig.baseURL,
            )

        hotwireNavigatorConfigurations = mutableListOf(configuration)
        hotwireTabs =
            listOf(
                HotwireBottomTab(
                    title = "loading…",
                    iconResId = resources.getIdentifier("material_circle", "drawable", packageName),
                    configuration = configuration,
                ),
            )
        tabsReady = false
    }

    private fun buildConfiguredTabs(
        tabs: List<Tab>,
        navigatorContainer: FrameLayout,
    ) {
        val containerIds = tabs.map { View.generateViewId() }
        val navigatorHosts = tabs.map { NavigatorHost() }

        hotwireNavigatorConfigurations =
            tabs
                .mapIndexed { index, tab ->
                    NavigatorConfiguration(
                        name = tab.title,
                        navigatorHostId = containerIds[index],
                        startLocation = normalizeLocation(tab.path.ifBlank { "/" }),
                    )
                }.toMutableList()

        hotwireTabs =
            tabs.mapIndexed { index, tab ->
                HotwireBottomTab(
                    title = tab.title,
                    iconResId = resources.getIdentifier("material_${tab.image}", "drawable", packageName),
                    configuration = hotwireNavigatorConfigurations[index],
                )
            }

        tabs.forEachIndexed { index, _ ->
            val containerView =
                FragmentContainerView(this).apply {
                    id = containerIds[index]
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                }
            navigatorContainer.addView(containerView)
        }

        tabs.forEachIndexed { index, _ ->
            supportFragmentManager
                .beginTransaction()
                .add(containerIds[index], navigatorHosts[index], "tab_$index")
                .commitNow()
        }

        tabsReady = true
    }

    private fun maybeHandleInitialNavigation() {
        if (!tabsReady || !::bottomNavigationController.isInitialized) return

        val deepLinkLocation = pendingDeepLinks.removeFirstOrNull() ?: return
        routeToLocation(deepLinkLocation)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val location = extractDeepLinkLocation(intent) ?: return
        pendingDeepLinks.addLast(location)
    }

    private fun extractDeepLinkLocation(intent: Intent?): String? {
        val path =
            intent
                ?.getStringExtra("path")
                ?.trim()
                .orEmpty()
                .ifBlank { intent?.dataString?.trim().orEmpty() }
        if (path.isBlank()) return null
        return normalizeLocation(path)
    }

    private fun normalizeLocation(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        return "${AppConfig.baseURL}/${path.trimStart('/')}"
    }

    private fun routeToLocation(location: String) {
        val targetTabIndex = findTabIndexForLocation(location)
        if (targetTabIndex >= 0) {
            selectTab(targetTabIndex)
        }

        window.decorView.post {
            delegate.currentNavigator?.route(location)
        }
    }

    private fun routeToTabRoot(index: Int) {
        val tab = AppConfig.tabs.getOrNull(index) ?: return
        val target = normalizeLocation(tab.path.ifBlank { "/" })
        delegate.currentNavigator?.route(target)
    }

    private fun readLastTabIndex(): Int? {
        val index = prefs.getInt(KEY_LAST_TAB_INDEX, -1)
        return if (index >= 0) index else null
    }

    private fun storeLastTabIndex(index: Int) {
        prefs.edit().putInt(KEY_LAST_TAB_INDEX, index).apply()
    }

    private fun selectTab(index: Int) {
        if (!::bottomNavigationController.isInitialized) return
        if (index !in AppConfig.tabs.indices) return

        val itemId = tabItemIds.getOrNull(index) ?: return
        if (bottomNavigationController.view.selectedItemId == itemId) return

        isProgrammaticTabSelection = true
        try {
            bottomNavigationController.view.selectedItemId = itemId
        } finally {
            isProgrammaticTabSelection = false
        }
    }

    private fun findTabIndexForLocation(location: String): Int {
        val normalizedLocation = canonicalLocation(normalizeLocation(location))
        return AppConfig.tabs
            .mapIndexed { index, tab ->
                index to canonicalLocation(normalizeLocation(tab.path.ifBlank { "/" }))
            }.filter { (_, tabRoot) ->
                normalizedLocation.startsWith(tabRoot)
            }.maxByOrNull { (_, tabRoot) ->
                tabRoot.length
            }?.first ?: -1
    }

    private fun canonicalLocation(location: String): String =
        if (location.length > AppConfig.baseURL.length) {
            location.trimEnd('/')
        } else {
            location
        }

    private fun canonicalLocationSafe(location: String): String = canonicalLocation(normalizeLocation(location))

    private fun isPlaceholderTabs(tabs: List<Tab>): Boolean =
        tabs.size == 1 &&
            tabs[0].title == "loading…" &&
            tabs[0].path.isBlank()

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
        private const val KEY_CACHED_TABS = "cached_tabs"
        private const val KEY_SCROLL_POSITIONS = "scroll_positions"
    }
}
