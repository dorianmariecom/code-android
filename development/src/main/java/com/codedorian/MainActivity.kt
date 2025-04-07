package com.codedorian

import android.os.Bundle
import android.view.Menu.*
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.hotwire.navigation.activities.HotwireActivity
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.util.applyDefaultImeWindowInsets

class MainActivity : HotwireActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.main).applyDefaultImeWindowInsets()
        tabsChanged(Tab.all)
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

    fun tabsChanged(tabs: List<Tab>) {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNavigation.menu.clear()

        tabs.forEach { tab ->
            val itemId = generateViewId()
            tab.itemId = itemId

            val item = bottomNavigation.menu.add(NONE, itemId, NONE, tab.title)
            val icon = resources.getIdentifier("material_${tab.image}", "drawable", packageName)
            item.setIcon(icon)
        }

        bottomNavigation.setOnItemSelectedListener { menuItem ->
            val selectedTab = tabs.firstOrNull { tab -> tab.itemId == menuItem.itemId }
            selectedTab?.let { tab ->
                route(tab.path)
                true
            } ?: false
        }

        Tab.all = tabs
    }

    override fun navigatorConfigurations(): List<NavigatorConfiguration> {
        return listOf(
            NavigatorConfiguration(
                name = "main",
                startLocation = Tab.default.url,
                navigatorHostId = R.id.main_navigator_host,
            )
        )

    }

    private fun route(path: String?) {
        delegate.currentNavigator?.route("${AppConfig.baseURL}/${path}")
    }
}
