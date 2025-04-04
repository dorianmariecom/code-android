package com.codedorian

import android.util.Log.d
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.Menu.*
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.*
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.*
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.hotwire.navigation.activities.HotwireActivity
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.navigator.NavigatorHost
import dev.hotwire.navigation.util.applyDefaultImeWindowInsets

class MainActivity : HotwireActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.main).applyDefaultImeWindowInsets()
        onTabsChanged()
    }

    override fun onStart() {
        super.onStart()
        handleDeepLink(intent)
    }

    override fun navigatorConfigurations(): List<NavigatorConfiguration> =
        Tab.all.map { tab ->
            NavigatorConfiguration(
                name = tab.title,
                startLocation = "${AppConfig.baseURL}/${tab.path}",
                navigatorHostId = tab.navigatorHostId!!
            )
        }

    private fun showTab(selectedTab: Tab) {
        Tab.all.forEach { tab ->
            val view = findViewById<View>(tab.navigatorHostId!!)!!
            view.visibility = if (selectedTab == tab) VISIBLE else GONE
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.let {
            if (intent.hasExtra("path")) {
                delegate.currentNavigator?.route("${AppConfig.baseURL}/${intent.getStringExtra("path")}")
            }
        }

        this.intent = null
    }

    override fun onResume() {
        super.onResume()
        handleDeepLink(intent)
    }

    fun onTabsChanged() {

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val main = findViewById<ViewGroup>(R.id.main)

        bottomNavigation.menu.clear()

        Tab.all.forEach { tab ->
            val itemId = generateViewId()
            tab.itemId = itemId

            val item = bottomNavigation.menu.add(NONE, itemId, NONE, tab.title)
            val icon = resources.getIdentifier("material_${tab.image}", "drawable", packageName)
            item.setIcon(icon)

            val containerViewId = generateViewId()

            tab.navigatorHostId = containerViewId

            val navigatorHostContainer = FragmentContainerView(this).apply {
                id = containerViewId
                layoutParams = LayoutParams(MATCH_PARENT, 0).apply {
                    topToTop = PARENT_ID
                    bottomToTop = R.id.bottom_navigation
                    startToStart = PARENT_ID
                    endToEnd = PARENT_ID
                }
            }

            main.addView(navigatorHostContainer)
        }

        val fragmentTransaction = supportFragmentManager.beginTransaction()

        Tab.all.forEach { tab ->
            val navigatorHost = NavigatorHost()

            fragmentTransaction.replace(tab.navigatorHostId!!, navigatorHost, "NavigatorHost_${tab.title}")
        }

        fragmentTransaction.commit()

        bottomNavigation.setOnItemSelectedListener { menuItem ->
            val selectedTab = Tab.all.firstOrNull { tab -> tab.itemId == menuItem.itemId }
            selectedTab?.let {
                showTab(it)
                true
            } ?: false
        }

        showTab(Tab.default())
    }
}

