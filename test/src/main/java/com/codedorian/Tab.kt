package com.codedorian

import android.util.Log.d
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentContainerView
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tab(
    @SerialName("title") val title: String,
    @SerialName("image") val image: String,
    @SerialName("path") val path: String,
    var itemId: Int? = null,
    var navigatorHostId: Int? = null
) : Comparable<Tab> {
    companion object {
        var all =
            listOf(
                Tab(
                    title = "loading…",
                    path = "",
                    image = "circle",
                    itemId = R.id.bottom_navigation_loading,
                    navigatorHostId = R.id.loading_navigator_host,
                ),
            )

        fun first(): Tab = all.first()
        fun last(): Tab = all.last()
        fun default(): Tab = all.first()
    }

    override fun compareTo(other: Tab): Int =
        compareValuesBy(this, other, { it.title }, { it.image }, { it.path })

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tab) return false

        return title == other.title && image == other.image &&  path == other.path
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + image.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }
}
