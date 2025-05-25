package com.codedorian

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
import dev.hotwire.navigation.tabs.navigatorConfigurations
import dev.hotwire.navigation.util.applyDefaultImeWindowInsets

class MainActivity : HotwireActivity() {
    private lateinit var bottomNavigationController: HotwireBottomNavigationController
    private var tabs: List<HotwireBottomTab> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        tabs = listOf(
            HotwireBottomTab(
                title = "loading…",
                iconResId = R.drawable.material_circle,
                configuration = NavigatorConfiguration(
                    name = "loading…",
                    navigatorHostId = R.id.navigator_host_loading,
                    startLocation = AppConfig.baseURL
                )
            )
        )

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.main).applyDefaultImeWindowInsets()

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationController = HotwireBottomNavigationController(this, bottomNavigationView)
        bottomNavigationController.load(tabs, 0)
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

    fun tabsChanged(messageTabs: List<TabBarComponent.MessageTab>) {
        val main = findViewById<ViewGroup>(R.id.main)

        tabs.forEach { tab ->
            val hostId = tab.configuration.navigatorHostId

            val fragment = supportFragmentManager.findFragmentById(hostId)

            fragment?.let {
                supportFragmentManager.beginTransaction()
                    .remove(it)
                    .commitNow()
            }

            val containerView = main.findViewById<View>(hostId)
            containerView?.let {
                main.removeView(it)
            }
        }

        val containerIds = messageTabs.map { View.generateViewId() }

        val navigatorHosts = messageTabs.map {
            supportFragmentManager.fragmentFactory.instantiate(
                classLoader,
                NavigatorHost::class.java.name
            ) as NavigatorHost
        }

        messageTabs.forEachIndexed { index, _ ->
            val containerView = FragmentContainerView(this).apply {
                id = containerIds[index]
                layoutParams = FrameLayout.LayoutParams(
                    MATCH_PARENT, MATCH_PARENT
                )
            }

            main.addView(containerView)
        }

        messageTabs.forEachIndexed { index, _ ->
            supportFragmentManager.beginTransaction()
                .add(containerIds[index], navigatorHosts[index], "tab_$index")
                .commit()
        }

        tabs = messageTabs.mapIndexed { index, tab ->
            HotwireBottomTab(
                title = tab.title,
                iconResId = resources.getIdentifier("material_${tab.image}", "drawable", packageName),
                configuration = NavigatorConfiguration(
                    name = tab.title,
                    navigatorHostId = containerIds[index],
                    startLocation = "${AppConfig.baseURL}/${tab.path}"
                )
            )
        }

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationController = HotwireBottomNavigationController(this, bottomNavigationView)
        bottomNavigationController.load(tabs)
    }

    override fun navigatorConfigurations() = tabs.navigatorConfigurations

    private fun route(path: String?) {
        delegate.currentNavigator?.route("${AppConfig.baseURL}/$path")
    }
}
