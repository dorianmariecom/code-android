package com.codedorian

import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.hotwire.navigation.activities.HotwireActivity
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.navigator.NavigatorHost
import dev.hotwire.navigation.tabs.HotwireBottomNavigationController
import dev.hotwire.navigation.tabs.HotwireBottomTab
import dev.hotwire.navigation.util.applyDefaultImeWindowInsets

class MainActivity : HotwireActivity() {
    private lateinit var bottomNavigationController: HotwireBottomNavigationController
    private var hotwireTabs: List<HotwireBottomTab> = emptyList()
    private var hotwireNavigatorConfigurations: MutableList<NavigatorConfiguration> =
        mutableListOf(
            NavigatorConfiguration(
                name = "loading…",
                navigatorHostId = R.id.navigator_host_loading,
                startLocation = AppConfig.baseURL,
            ),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    override fun onStart() {
        super.onStart()

        intent?.let {
            if (intent.hasExtra("path")) {
                route(intent.getStringExtra("path"))
            }
        }

        this.intent = null
    }

    override fun onResume() {
        super.onResume()

        intent?.let {
            if (intent.hasExtra("path")) {
                route(intent.getStringExtra("path"))
            }
        }

        this.intent = null
    }

    fun tabsChanged() {
        val tabs = AppConfig.tabs
        val navigatorContainer = findViewById<FrameLayout>(R.id.navigator_container)
        val containerIds = tabs.map { View.generateViewId() }
        val navigatorHosts = tabs.map { NavigatorHost() }
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNavigationView.menu.clear()

        hotwireTabs =
            tabs.mapIndexed { index, tab ->
                val navigatorConfiguration =
                    NavigatorConfiguration(
                        name = tab.title,
                        navigatorHostId = containerIds[index],
                        startLocation = "${AppConfig.baseURL}${tab.path}",
                    )

                hotwireNavigatorConfigurations.add(navigatorConfiguration)

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
        bottomNavigationController.load(hotwireTabs)
    }

    override fun navigatorConfigurations() = hotwireNavigatorConfigurations

    private fun route(path: String?) {
        delegate.currentNavigator?.route("${AppConfig.baseURL}/$path")
    }
}
