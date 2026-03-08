package com.codedorian

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.hotwire.core.turbo.visit.VisitAction
import dev.hotwire.core.turbo.visit.VisitOptions
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
    private var skipNextTabRootNavigation = false
    private var isProgrammaticTabSelection = false
    private var isHandlingTabRetap = false
    private var preloadedTabs = false
    private var tabStates: MutableMap<Int, TabState> = mutableMapOf()
    private val pendingScrollRestore: MutableMap<Int, Int> = mutableMapOf()
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dynamic navigator hosts are recreated from persisted app state, so we avoid
        // FragmentManager restoring stale hosts whose ids/configs may no longer exist.
        super.onCreate(null)
        tabStates = readTabStates()
        logd("onCreate: savedInstanceState=${savedInstanceState != null}, tabStates=${tabStatesSummary(tabStates)}")
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
        logd("tabsChanged: tabsReady=$areTabsReady tabs=${tabs.map { "${it.title}:${it.path}" }}")
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

        preloadedTabs = false
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
                    logd(
                        "tabsChanged: seed pendingScrollRestore tab=$index y=${lastState.scrollY} start=${navigatorConfiguration.startLocation}",
                    )
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
        logd("tabsChanged: load hotwire tabs size=${hotwireTabs.size} initialTabIndex=0")
        bottomNavigationController.load(hotwireTabs, selectedTabIndex = 0)
        tabItemIds =
            List(bottomNavigationView.menu.size()) { index ->
                bottomNavigationView.menu.getItem(index).itemId
            }
        preloadTabs()
        tabsReady = areTabsReady
        bottomNavigationController.setOnTabSelectedListener { index, _ ->
            if (!tabsReady) return@setOnTabSelectedListener
            storeLastTabIndex(index)
            if (skipNextTabRootNavigation) {
                skipNextTabRootNavigation = false
                return@setOnTabSelectedListener
            }
            routeToTabRoot(index)
        }
        bottomNavigationView.setOnItemReselectedListener { item ->
            if (!tabsReady) return@setOnItemReselectedListener
            if (isProgrammaticTabSelection || isHandlingTabRetap) return@setOnItemReselectedListener
            val index = tabItemIds.indexOf(item.itemId).takeIf { it >= 0 } ?: item.itemId
            bottomNavigationView.post { forceFetchOnTabRetap(index) }
        }
        maybeHandleInitialNavigation()
    }

    override fun navigatorConfigurations() = hotwireNavigatorConfigurations

    fun persistLastTabLocation(
        location: String,
        navigator: Navigator,
    ) = persistLastTabLocation(location)

    fun persistLastTabLocation(location: String) {
        if (!::bottomNavigationController.isInitialized) return
        if (location.isBlank()) return

        val selectedTabIndex = selectedTabIndex() ?: return
        val resolvedTabIndex = tabIndexForLocation(location)
        val tabIndex = resolvedTabIndex ?: selectedTabIndex
        logd(
            "persistLastTabLocation: location=$location selectedTab=$selectedTabIndex resolvedTab=$resolvedTabIndex chosenTab=$tabIndex",
        )
        if (resolvedTabIndex != null && resolvedTabIndex != selectedTabIndex) {
            logd("persistLastTabLocation: drop write because resolvedTab != selectedTab")
            return
        }
        logd("persistLastTabLocation: tab=$tabIndex location=$location")
        storeTabState(tabIndex = tabIndex, location = location)
    }

    fun persistCurrentTabScroll(
        scrollY: Int,
        navigator: Navigator,
        location: String? = null,
    ) = persistCurrentTabScroll(scrollY, location)

    fun persistCurrentTabScroll(
        scrollY: Int,
        location: String? = null,
    ) {
        if (!::bottomNavigationController.isInitialized) {
            logd("persistCurrentTabScroll: skip because bottomNavigationController is not initialized")
            return
        }
        if (scrollY < 0) {
            logd("persistCurrentTabScroll: skip negative scrollY=$scrollY location=${location ?: "n/a"}")
            return
        }

        val selectedTabIndex =
            selectedTabIndex() ?: run {
                logd("persistCurrentTabScroll: skip because selectedTabIndex is null")
                return
            }
        val resolvedTabIndex = location?.let { tabIndexForLocation(it) }
        val tabIndex = resolvedTabIndex ?: selectedTabIndex
        logd(
            "persistCurrentTabScroll: selectedTab=$selectedTabIndex resolvedTab=$tabIndex location=${location ?: "n/a"} scrollY=$scrollY",
        )
        if (resolvedTabIndex != null && resolvedTabIndex != selectedTabIndex) {
            logd("persistCurrentTabScroll: drop write because resolvedTab != selectedTab")
            return
        }
        logd("persistCurrentTabScroll: tab=$tabIndex scrollY=$scrollY location=${location ?: "n/a"}")
        storeTabState(tabIndex = tabIndex, scrollY = scrollY)
    }

    fun consumePendingScrollRestore(navigator: Navigator): Int? {
        if (!::bottomNavigationController.isInitialized) {
            logd("consumePendingScrollRestore(navigator): skip because bottomNavigationController is not initialized")
            return null
        }

        val tabIndex =
            selectedTabIndex() ?: run {
                logd("consumePendingScrollRestore(navigator): skip because selectedTabIndex is null")
                return null
            }
        val value = pendingScrollRestore.remove(tabIndex)
        if (value != null) {
            logd("consumePendingScrollRestore(navigator): tab=$tabIndex y=$value")
        } else {
            logd("consumePendingScrollRestore(navigator): no pending value for tab=$tabIndex")
        }
        return value
    }

    fun consumePendingScrollRestore(location: String): Int? {
        if (!::bottomNavigationController.isInitialized) {
            logd("consumePendingScrollRestore(location): skip because bottomNavigationController is not initialized")
            return null
        }
        val tabIndex =
            tabIndexForLocation(location) ?: run {
                logd("consumePendingScrollRestore(location): no tab for location=$location")
                return null
            }
        val value = pendingScrollRestore.remove(tabIndex)
        if (value != null) {
            logd("consumePendingScrollRestore(location): tab=$tabIndex y=$value location=$location")
        } else {
            logd("consumePendingScrollRestore(location): no pending value for tab=$tabIndex location=$location")
        }
        return value
    }

    fun isNavigatorActive(navigator: Navigator): Boolean = delegate.currentNavigator == navigator

    fun savedScrollForLocation(location: String): Int? {
        val tabIndex =
            tabIndexForLocation(location) ?: run {
                logd("savedScrollForLocation: no tab for location=$location")
                return null
            }
        val saved = tabStates[tabIndex]?.scrollY
        logd("savedScrollForLocation: tab=$tabIndex location=$location saved=${saved ?: "n/a"}")
        return saved
    }

    private fun maybeHandleInitialNavigation() {
        if (!tabsReady || !::bottomNavigationController.isInitialized) {
            logd(
                "maybeHandleInitialNavigation: waiting tabsReady=$tabsReady controllerInitialized=${::bottomNavigationController.isInitialized}",
            )
            return
        }

        val deepLinkLocation = pendingDeepLinks.removeFirstOrNull()
        if (deepLinkLocation != null) {
            shouldRestoreLastState = false
            logd("maybeHandleInitialNavigation: deepLink=$deepLinkLocation")
            routeToLocation(deepLinkLocation)
            return
        }

        if (shouldRestoreLastState) {
            shouldRestoreLastState = false
            logd("maybeHandleInitialNavigation: restoring last state")
            restoreLastTabLocation()
        }
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val location = extractDeepLinkLocation(intent) ?: return
        logd("handleDeepLinkIntent: queued location=$location")
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
        val tabIndex =
            readLastTabIndex() ?: run {
                logd("restoreLastTabLocation: no last tab index")
                return
            }
        val location =
            tabStates[tabIndex]?.location ?: run {
                logd("restoreLastTabLocation: no saved location for tab=$tabIndex")
                return
            }
        val resolvedTabIndex = tabIndexForLocation(location)
        logd(
            "restoreLastTabLocation: savedTab=$tabIndex resolvedTab=$resolvedTabIndex location=$location states=${tabStatesSummary(
                tabStates,
            )}",
        )
        if (tabIndex in AppConfig.tabs.indices) {
            selectTab(tabIndex)
            val route =
                if (resolvedTabIndex == null || resolvedTabIndex == tabIndex) {
                    location
                } else {
                    val fallback = normalizeLocation(AppConfig.tabs[tabIndex].path.ifBlank { "/" })
                    logd(
                        "restoreLastTabLocation: saved location belongs to another tab, using tab root tab=$tabIndex fallback=$fallback",
                    )
                    fallback
                }
            logd("restoreLastTabLocation: tab=$tabIndex route=$route")
            window.decorView.post {
                delegate.currentNavigator?.route(route)
            }
            return
        }
        logd("restoreLastTabLocation: fallback route=$location")
        routeToLocation(location)
    }

    private fun routeToLocation(location: String) {
        val selectedBefore = selectedTabIndex()
        val targetTabIndex = findTabIndexForLocation(location)
        val specificTargetTabIndex =
            targetTabIndex.takeIf { index ->
                isSpecificTabMatch(location, index)
            }
        if (specificTargetTabIndex != null) {
            selectTab(specificTargetTabIndex)
        } else {
            val fallbackTabIndex =
                readLastTabIndex()?.takeIf { it in AppConfig.tabs.indices }
                    ?: selectedBefore.takeIf { it in AppConfig.tabs.indices }
            if (fallbackTabIndex != null && fallbackTabIndex in AppConfig.tabs.indices) {
                logd(
                    "routeToLocation: no specific tab for location=$location, fallbackTab=$fallbackTabIndex",
                )
                selectTab(fallbackTabIndex)
            } else {
                logd("routeToLocation: no specific tab for location=$location and no fallback tab")
            }
        }
        logd(
            "routeToLocation: selectedBefore=$selectedBefore targetTab=$targetTabIndex specificTargetTab=$specificTargetTabIndex selectedAfter=${selectedTabIndex()} location=$location",
        )
        window.decorView.post {
            delegate.currentNavigator?.route(location)
        }
    }

    private fun isSpecificTabMatch(
        location: String,
        tabIndex: Int,
    ): Boolean {
        val tab = AppConfig.tabs.getOrNull(tabIndex) ?: return false
        val normalizedLocation = canonicalLocation(normalizeLocation(location))
        val tabRoot = canonicalLocation(normalizeLocation(tab.path.ifBlank { "/" }))
        val rootTab = canonicalLocation(normalizeLocation("/"))

        // A "/" tab is a catch-all; only treat it as specific for the root page itself.
        return if (tabRoot == rootTab) {
            normalizedLocation == rootTab
        } else {
            normalizedLocation.startsWith(tabRoot)
        }
    }

    private fun routeToTabRoot(index: Int) {
        val tab = AppConfig.tabs.getOrNull(index) ?: return
        val tabLocation = normalizeLocation(tab.path.ifBlank { "/" })
        logd("routeToTabRoot: tab=$index location=$tabLocation")
        delegate.currentNavigator?.route(tabLocation)
    }

    private fun preloadTabs() {
        if (preloadedTabs) return
        if (!::bottomNavigationController.isInitialized) return
        if (hotwireTabs.size <= 1) {
            preloadedTabs = true
            return
        }
        val initialTabIndex = selectedTabIndex() ?: 0
        logd("preloadTabs: initialTab=$initialTabIndex count=${hotwireTabs.size}")
        hotwireTabs.indices.forEach { index ->
            if (index == initialTabIndex) return@forEach
            bottomNavigationController.selectTab(index)
        }
        bottomNavigationController.selectTab(initialTabIndex)
        preloadedTabs = true
    }

    private fun forceFetchOnTabRetap(index: Int) {
        if (isHandlingTabRetap) {
            logd("forceFetchOnTabRetap: skip re-entry for tab=$index")
            return
        }
        val tab = AppConfig.tabs.getOrNull(index) ?: return
        val tabLocation = normalizeLocation(tab.path.ifBlank { "/" })
        logd("forceFetchOnTabRetap: tab=$index location=$tabLocation")
        isHandlingTabRetap = true
        try {
            storeLastTabIndex(index)
            skipNextTabRootNavigation = true
            bottomNavigationController.route(tabLocation, VisitOptions(action = VisitAction.REPLACE), null, null)
        } finally {
            isHandlingTabRetap = false
        }
    }

    private fun readLastTabIndex(): Int? {
        val index = prefs.getInt(KEY_LAST_TAB_INDEX, -1)
        if (index >= 0) {
            logd("readLastTabIndex: $index")
            return index
        }
        logd("readLastTabIndex: none")
        return null
    }

    private fun storeLastTabIndex(index: Int) {
        prefs.edit().putInt(KEY_LAST_TAB_INDEX, index).commit()
        logd("storeLastTabIndex: $index")
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
        logd(
            "storeTabState: tab=$tabIndex locationIn=${location ?: "n/a"} scrollIn=${scrollY ?: -1} state=${updated.location}#${updated.scrollY}",
        )
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

        logd("readTabStates: ${tabStatesSummary(result)}")
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
        logd("storeTabStates: ${tabStatesSummary(tabStates)}")
    }

    private fun selectedTabIndex(): Int? {
        val selectedItemId = bottomNavigationController.view.selectedItemId
        val mappedIndex = tabItemIds.indexOf(selectedItemId)
        if (mappedIndex >= 0) return mappedIndex
        val fallback = selectedItemId.takeIf { it in AppConfig.tabs.indices }
        logd(
            "selectedTabIndex: selectedItemId=$selectedItemId mappedIndex=$mappedIndex fallback=$fallback tabItemIds=$tabItemIds",
        )
        return fallback
    }

    private fun selectTab(index: Int) {
        if (!::bottomNavigationController.isInitialized) return
        if (index !in AppConfig.tabs.indices) return

        val itemId = tabItemIds.getOrNull(index) ?: index
        val currentItemId = bottomNavigationController.view.selectedItemId
        if (currentItemId == itemId) {
            logd("selectTab: skip already selected index=$index itemId=$itemId")
            return
        }
        logd("selectTab: index=$index itemId=$itemId tabItemIds=$tabItemIds")
        skipNextTabRootNavigation = true
        isProgrammaticTabSelection = true
        try {
            bottomNavigationController.view.selectedItemId = itemId
        } finally {
            isProgrammaticTabSelection = false
        }
    }

    private fun tabIndexForLocation(location: String): Int? {
        val resolved = findTabIndexForLocation(location)
        logd("tabIndexForLocation: location=$location resolved=$resolved")
        return resolved.takeIf { it >= 0 }
    }

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
        logd(
            "migrateTabStateKeysIfNeeded: changed=$changed oldLast=$oldLastTabIndex newLast=$migratedLastTabIndex states=${tabStatesSummary(
                tabStates,
            )}",
        )
    }

    private fun findTabIndexForLocation(location: String): Int {
        val normalizedLocation = canonicalLocation(normalizeLocation(location))
        val candidates =
            AppConfig.tabs
                .mapIndexed { index, tab ->
                    val tabRoot = canonicalLocation(normalizeLocation(tab.path.ifBlank { "/" }))
                    index to tabRoot
                }
        val matching = candidates.filter { (_, tabRoot) -> normalizedLocation.startsWith(tabRoot) }
        val resolved = matching.maxByOrNull { (_, tabRoot) -> tabRoot.length }?.first ?: -1
        logd(
            "findTabIndexForLocation: location=$location normalized=$normalizedLocation candidates=$candidates matching=$matching resolved=$resolved",
        )
        return resolved
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
        private const val TAG = "MainActivity"
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

    private fun logd(message: String) {
        Log.d(TAG, message)
    }

    private fun tabStatesSummary(states: Map<Int, TabState>): String =
        states.entries
            .sortedBy { it.key }
            .joinToString(prefix = "[", postfix = "]") { (index, state) ->
                "$index:${state.location}#${state.scrollY}"
            }
}
