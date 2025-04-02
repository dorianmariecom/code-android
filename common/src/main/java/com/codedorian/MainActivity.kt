package com.codedorian

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.hotwire.navigation.activities.HotwireActivity
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.util.applyDefaultImeWindowInsets

class MainActivity : HotwireActivity() {
    private val tabs = Tab.all

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.main).applyDefaultImeWindowInsets()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { tab ->
            val selectedTab = tabs.first { it.menuId == tab.itemId }
            showTab(selectedTab)
            true
        }
        showTab(tabs.first())
    }

    override fun navigatorConfigurations() =
        tabs.map { tab ->
            NavigatorConfiguration(
                name = tab.name,
                startLocation = "${AppConfig.baseURL}/${tab.path}",
                navigatorHostId = tab.navigatorHostId,
            )
        }

    private fun showTab(tab: Tab) {
        tabs.forEach {
            val view = findViewById<View>(it.navigatorHostId)
            view.visibility = if (it == tab) View.VISIBLE else View.GONE
        }
    }
}
