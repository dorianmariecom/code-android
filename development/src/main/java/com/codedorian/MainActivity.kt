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
    private lateinit var bottomNavigationController: HotwireBottomNavigationController
    private var hotwireTabs: List<HotwireBottomTab> = listOf(
        HotwireBottomTab(
            title = "loading…",
            iconResId = R.drawable.material_circle,
            configuration = NavigatorConfiguration(
                name = "loading…",
                navigatorHostId = R.id.navigator_host_0,
                startLocation = AppConfig.baseURL
            )
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        findViewById<View>(R.id.main).applyDefaultImeWindowInsets()
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.menu.clear()
        bottomNavigationController = HotwireBottomNavigationController(this, bottomNavigation)
        bottomNavigationController.load(hotwireTabs)
        setContentView(R.layout.activity_main)
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
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNavigation.menu.clear()

        hotwireTabs = Tab.all.mapIndexed { index, tab ->
            HotwireBottomTab(
                title = tab.title,
                iconResId = resources.getIdentifier(
                    "material_${tab.image}",
                    "drawable",
                    packageName
                ),
                configuration = NavigatorConfiguration(
                    name = tab.title,
                    navigatorHostId = resources.getIdentifier(
                        "navigator_host_${index}",
                        "id",
                        packageName
                    ),
                    startLocation = tab.url
                )
            )
        }


        bottomNavigationController = HotwireBottomNavigationController(this, bottomNavigation)
        bottomNavigationController.load(hotwireTabs)
    }

    override fun navigatorConfigurations() = hotwireTabs.navigatorConfigurations

    private fun route(path: String?) {
        delegate.currentNavigator?.route("${ApwpConfig.baseURL}/$path")
    }
}
