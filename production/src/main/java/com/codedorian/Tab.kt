package com.codedorian

import androidx.annotation.IdRes

data class Tab(
    val name: String,
    val path: String,
    @IdRes val menuId: Int,
    @IdRes val navigatorHostId: Int
) {
    companion object {
        val all = listOf(
            Tab(
                name = "home",
                path = "",
                menuId = R.id.bottom_navigation_home,
                navigatorHostId = R.id.home_navigator_host,
            ),
            Tab(
                name = "programs",
                path = "programs",
                menuId = R.id.bottom_navigation_programs,
                navigatorHostId = R.id.programs_navigator_host
            )
        )
    }
}
