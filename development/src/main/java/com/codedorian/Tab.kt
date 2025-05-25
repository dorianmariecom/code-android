package com.codedorian

import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.tabs.HotwireBottomTab

private val loadingTab = HotwireBottomTab(
    title = "loading…",
    iconResId = R.drawable.material_circle,
    configuration = NavigatorConfiguration(
        name = "loading…",
        navigatorHostId = R.id.navigator_host_loading,
        startLocation = AppConfig.baseURL
    )
)

var tabs = listOf(
    loadingTab,
)
