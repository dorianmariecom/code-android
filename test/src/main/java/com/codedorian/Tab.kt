package com.codedorian

import androidx.annotation.IdRes

data class Tab(
    val name: String,
    val path: String,
    @IdRes val menuId: Int,
    @IdRes val navigatorHostId: Int,
) {
    companion object {
        val all =
            listOf(
                Tab(
                    name = "home",
                    path = "programs/new",
                    menuId = R.id.bottom_navigation_home,
                    navigatorHostId = R.id.home_navigator_host,
                ),
                Tab(
                    name = "programs",
                    path = "programs",
                    menuId = R.id.bottom_navigation_programs,
                    navigatorHostId = R.id.programs_navigator_host,
                ),
                Tab(
                    name = "messages",
                    path = "messages",
                    menuId = R.id.bottom_navigation_messages,
                    navigatorHostId = R.id.messages_navigator_host,
                ),
                Tab(
                    name = "account",
                    path = "account",
                    menuId = R.id.bottom_navigation_account,
                    navigatorHostId = R.id.account_navigator_host,
                ),
                Tab(
                    name = "more",
                    path = "more",
                    menuId = R.id.bottom_navigation_more,
                    navigatorHostId = R.id.more_navigator_host,
                ),
            )
    }
}
