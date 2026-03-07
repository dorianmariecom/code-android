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
    private var shouldRestoreLastState = true
    private var tabStates: MutableMap<Int, TabState> = mutableMapOf()
    private val pendingScrollRestore: MutableMap<Int, Int> = mutableMapOf()
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabStates = readTabStates()
        handleDeepLinkIntent(intent)
        AppConfig.tabs =
            listOf(
                Tab(
                    title = "loading…",
                    image = "circle",
                    path = "",
                ),
            )

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
        val tabs = AppConfig.tabs
        val areTabsReady = !isPlaceholderTabs(tabs)
        if (areTabsReady) {
            migrateTabStateKeysIfNeeded(tabs)
        }
        val navigatorContainer = findViewById<FrameLayout>(R.id.navigator_container)
        val containerIds = tabs.map { View.generateViewId() }
        val navigatorHosts = tabs.map { NavigatorHost() }
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val baseConfiguration =
            NavigatorConfiguration(
                name = "loading…",
                navigatorHostId = R.id.navigator_host_loading,
                startLocation = AppConfig.baseURL,
            )

        hotwireNavigatorConfigurations = mutableListOf(baseConfiguration)
        navigatorContainer.removeAllViews()
        bottomNavigationView.menu.clear()

        hotwireTabs =
            tabs.mapIndexed { index, tab ->
                val lastState = tabStates[index]
                val navigatorConfiguration =
                    NavigatorConfiguration(
                        name = tab.title,
                        navigatorHostId = containerIds[index],
                        startLocation = lastState?.location ?: "${AppConfig.baseURL}${tab.path}",
                    )

                hotwireNavigatorConfigurations.add(navigatorConfiguration)
                if (lastState != null && lastState.scrollY > 0) {
                    pendingScrollRestore[index] = lastState.scrollY
                }

                HotwireBottomTab(
                    title = tab.title,
                    iconResId = resources.getIdentifier("material_${tab.image}", "drawable", packageName),
                    configuration = navigatorConfiguration,
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

        bottomNavigationController = HotwireBottomNavigationController(this, bottomNavigationView)
        val storedTabIndex = if (pendingDeepLinks.isEmpty() && areTabsReady) readLastTabIndex() else null
        bottomNavigationController.load(hotwireTabs, selectedTabIndex = 0)
        tabItemIds =
            List(bottomNavigationView.menu.size()) { index ->
                bottomNavigationView.menu.getItem(index).itemId
            }
        tabsReady = areTabsReady
        bottomNavigationController.setOnTabSelectedListener { index, _ ->
            if (!tabsReady) return@setOnTabSelectedListener
            storeLastTabIndex(index)
        }
        maybeHandleInitialNavigation()
    }

    override fun navigatorConfigurations() = hotwireNavigatorConfigurations

    fun persistLastTabLocation(
        location: String,
        navigator: Navigator,
    ) {
        if (!::bottomNavigationController.isInitialized) return
        if (location.isBlank()) return

        val tabIndex =
            if (delegate.currentNavigator == navigator) {
                selectedTabIndex() ?: tabIndexForLocation(location) ?: return
            } else {
                tabIndexForLocation(location) ?: return
            }
        storeTabState(tabIndex = tabIndex, location = location)
    }

    fun persistCurrentTabScroll(
        scrollY: Int,
        navigator: Navigator,
        location: String? = null,
    ) {
        if (!::bottomNavigationController.isInitialized) return
        if (scrollY < 0) return

        val tabIndex =
            location?.let { tabIndexForLocation(it) }
                ?: selectedTabIndex()
                ?: return
        storeTabState(tabIndex = tabIndex, scrollY = scrollY)
    }

    fun consumePendingScrollRestore(navigator: Navigator): Int? {
        if (!::bottomNavigationController.isInitialized) return null

        val tabIndex = selectedTabIndex() ?: return null
        val value = pendingScrollRestore.remove(tabIndex)
        return value
    }

    fun consumePendingScrollRestore(location: String): Int? {
        if (!::bottomNavigationController.isInitialized) return null
        val tabIndex = tabIndexForLocation(location) ?: return null
        val value = pendingScrollRestore.remove(tabIndex)
        return value
    }

    fun isNavigatorActive(navigator: Navigator): Boolean = delegate.currentNavigator == navigator

    fun savedScrollForLocation(location: String): Int? {
        val tabIndex = tabIndexForLocation(location) ?: return null
        return tabStates[tabIndex]?.scrollY
    }

    private fun maybeHandleInitialNavigation() {
        if (!tabsReady || !::bottomNavigationController.isInitialized) return

        val deepLinkLocation = pendingDeepLinks.removeFirstOrNull()
        if (deepLinkLocation != null) {
            shouldRestoreLastState = false
            routeToLocation(deepLinkLocation)
            return
        }

        if (shouldRestoreLastState) {
            shouldRestoreLastState = false
            restoreLastTabLocation()
        }
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

    private fun restoreLastTabLocation() {
        val tabIndex = readLastTabIndex() ?: return
        val location = tabStates[tabIndex]?.location ?: return
        if (tabIndex in AppConfig.tabs.indices) {
            selectTab(tabIndex)
            delegate.currentNavigator?.route(location)
            return
        }
        routeToLocation(location)
    }

    private fun routeToLocation(location: String) {
        val targetTabIndex = findTabIndexForLocation(location)
        if (targetTabIndex >= 0) {
            selectTab(targetTabIndex)
        }
        delegate.currentNavigator?.route(location)
    }

    private fun readLastTabIndex(): Int? {
        val index = prefs.getInt(KEY_LAST_TAB_INDEX, -1)
        return if (index >= 0) index else null
    }

    private fun storeLastTabIndex(index: Int) {
        prefs.edit().putInt(KEY_LAST_TAB_INDEX, index).commit()
    }

    private fun storeTabState(
        tabIndex: Int,
        location: String? = null,
        scrollY: Int? = null,
    ) {
        val current = tabStates[tabIndex]
        val updated =
            TabState(
                location = location ?: current?.location.orEmpty(),
                scrollY = scrollY ?: current?.scrollY ?: 0,
            )
        tabStates[tabIndex] = updated
        storeTabStates()
    }

    private fun readTabStates(): MutableMap<Int, TabState> {
        val raw = prefs.getString(KEY_TAB_STATES, null) ?: return mutableMapOf()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return mutableMapOf()
        val result = mutableMapOf<Int, TabState>()

        json.keys().forEach { key ->
            val index = key.toIntOrNull() ?: return@forEach
            val state = json.optJSONObject(key) ?: return@forEach
            val location = state.optString(KEY_LOCATION, "")
            val scrollY = state.optInt(KEY_SCROLL_Y, 0)
            if (location.isNotBlank()) {
                result[index] = TabState(location = location, scrollY = scrollY)
            }
        }

        return result
    }

    private fun storeTabStates() {
        val json = JSONObject()
        tabStates.forEach { (index, state) ->
            if (state.location.isBlank()) return@forEach
            val stateJson =
                JSONObject().apply {
                    put(KEY_LOCATION, state.location)
                    put(KEY_SCROLL_Y, state.scrollY)
                }
            json.put(index.toString(), stateJson)
        }
        prefs.edit().putString(KEY_TAB_STATES, json.toString()).commit()
    }

    private fun selectedTabIndex(): Int? {
        val selectedItemId = bottomNavigationController.view.selectedItemId
        val mappedIndex = tabItemIds.indexOf(selectedItemId)
        if (mappedIndex >= 0) return mappedIndex
        return selectedItemId.takeIf { it in AppConfig.tabs.indices }
    }

    private fun selectTab(index: Int) {
        if (!::bottomNavigationController.isInitialized) return
        if (index !in AppConfig.tabs.indices) return

        val itemId = tabItemIds.getOrNull(index) ?: index
        bottomNavigationController.view.selectedItemId = itemId
    }

    private fun tabIndexForLocation(location: String): Int? = findTabIndexForLocation(location).takeIf { it >= 0 }

    private fun migrateTabStateKeysIfNeeded(tabs: List<Tab>) {
        if (tabs.isEmpty() || tabStates.isEmpty()) return

        val oldStates = tabStates.toMap()
        var changed = false
        val migrated = mutableMapOf<Int, TabState>()

        oldStates.forEach { (key, state) ->
            val normalizedKey =
                when {
                    key in tabs.indices -> key
                    else -> findTabIndexForLocation(state.location, tabs)
                }

            if (normalizedKey >= 0 && migrated[normalizedKey] == null) {
                migrated[normalizedKey] = state
                if (normalizedKey != key) changed = true
            }
        }

        val oldLastTabIndex = readLastTabIndex()
        val migratedLastTabIndex =
            when {
                oldLastTabIndex == null -> {
                    null
                }

                oldLastTabIndex in tabs.indices -> {
                    oldLastTabIndex
                }

                else -> {
                    oldStates[oldLastTabIndex]?.let { state ->
                        findTabIndexForLocation(state.location, tabs).takeIf { it >= 0 }
                    }
                }
            }

        if (!changed && migratedLastTabIndex == oldLastTabIndex) return

        tabStates = migrated
        storeTabStates()
        migratedLastTabIndex?.let { storeLastTabIndex(it) }
    }

    private fun findTabIndexForLocation(location: String): Int {
        val normalizedLocation = canonicalLocation(normalizeLocation(location))
        return AppConfig.tabs
            .mapIndexed { index, tab ->
                val tabRoot = canonicalLocation(normalizeLocation(tab.path.ifBlank { "/" }))
                index to tabRoot
            }.filter { (_, tabRoot) -> normalizedLocation.startsWith(tabRoot) }
            .maxByOrNull { (_, tabRoot) -> tabRoot.length }
            ?.first ?: -1
    }

    private fun findTabIndexForLocation(
        location: String,
        tabs: List<Tab>,
    ): Int {
        val normalizedLocation = canonicalLocation(normalizeLocation(location))
        return tabs
            .mapIndexed { index, tab ->
                val tabRoot = canonicalLocation(normalizeLocation(tab.path.ifBlank { "/" }))
                index to tabRoot
            }.filter { (_, tabRoot) -> normalizedLocation.startsWith(tabRoot) }
            .maxByOrNull { (_, tabRoot) -> tabRoot.length }
            ?.first ?: -1
    }

    private fun canonicalLocation(location: String): String =
        if (location.length > AppConfig.baseURL.length) {
            location.trimEnd('/')
        } else {
            location
        }

    private fun isPlaceholderTabs(tabs: List<Tab>): Boolean =
        tabs.size == 1 &&
            tabs[0].title == "loading…" &&
            tabs[0].path.isBlank()

    companion object {
        private const val PREFS_NAME = "app_state"
        private const val KEY_LAST_TAB_INDEX = "last_tab_index"
        private const val KEY_TAB_STATES = "tab_states"
        private const val KEY_LOCATION = "location"
        private const val KEY_SCROLL_Y = "scroll_y"
    }

    data class TabState(
        val location: String,
        val scrollY: Int,
    )
}
