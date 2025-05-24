package com.codedorian

import android.content.res.Resources
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.tabs.HotwireBottomTab
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tab(
    @SerialName("title") val title: String,
    @SerialName("image") val image: String,
    @SerialName("path") val path: String,
    @SerialName("default") val default: Boolean,
    var itemId: Int? = null,
) : Comparable<Tab> {
    companion object {
        var all =
            listOf(
                Tab(
                    title = "loading…",
                    path = "",
                    image = "circle",
                    itemId = R.id.bottom_navigation_loading,
                    default = true,
                ),
            )

        val default: Tab get() = all.find { it.default } ?: all.first()

        fun toTabs(resources: Resources, packageName: String): List<HotwireBottomTab> {
            return all.mapIndexed { index, tab ->
                HotwireBottomTab(
                    title = tab.title,
                    iconResId = resources.getIdentifier("material_${tab.image}", "drawable", packageName),
                    configuration = NavigatorConfiguration(
                        name = tab.title,
                        navigatorHostId = resources.getIdentifier("navigator_host_${index}", "id", packageName),
                        startLocation = "${AppConfig.baseURL}/${tab.path}"
                    )
                )
            }
        }
    }

    val url: String get() = "${AppConfig.baseURL}/$path"

    override fun compareTo(other: Tab): Int = compareValuesBy(this, other, { it.title }, { it.image }, { it.path })

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tab) return false

        return title == other.title && image == other.image && path == other.path
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + image.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }
}
