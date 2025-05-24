package com.codedorian

import android.os.Bundle
import android.view.Menu.*
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.hotwire.navigation.activities.HotwireActivity
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.tabs.HotwireBottomNavigationController
import dev.hotwire.navigation.tabs.HotwireBottomTab
import dev.hotwire.navigation.tabs.navigatorConfigurations
import dev.hotwire.navigation.util.applyDefaultImeWindowInsets

class MainActivity : HotwireActivity() {
    private var bottomNavigationController: HotwireBottomNavigationController? = null

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
        Tab.all = tabs

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNavigation.menu.clear()

        bottomNavigationController = HotwireBottomNavigationController(this, bottomNavigation)
        bottomNavigationController?.load(Tab.toTabs(resources, packageName))
    }

    override fun navigatorConfigurations() = Tab.toTabs(resources, packageName).navigatorConfigurations

    private fun route(path: String?) {
        delegate.currentNavigator?.route("${AppConfig.baseURL}/$path")
    }
}
