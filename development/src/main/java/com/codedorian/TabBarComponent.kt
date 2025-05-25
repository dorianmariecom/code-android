package com.codedorian

import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.fragment.app.FragmentContainerView
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.tabs.HotwireBottomTab
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TabBarComponent(
    name: String,
    private val bridgeDelegate: BridgeDelegate<HotwireDestination>,
) : BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    private var messageTabs: List<MessageTab> = emptyList()
    private val containerIds = mutableListOf<Int>()

    @Serializable
    data class MessageTab(
        @SerialName("title") val title: String,
        @SerialName("image") val image: String,
        @SerialName("path") val path: String,
    ) : Comparable<MessageTab> {
        override fun compareTo(other: MessageTab): Int = compareValuesBy(this, other, { it.title }, { it.image }, { it.path })

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MessageTab) return false

            return title == other.title && image == other.image && path == other.path
        }

        override fun hashCode(): Int {
            var result = title.hashCode()
            result = 31 * result + image.hashCode()
            result = 31 * result + path.hashCode()
            return result
        }
    }

    @Serializable
    data class MessageData(
        @SerialName("tabs") val tabs: List<MessageTab>,
    )

    override fun onReceive(message: Message) {
        if (message.event == "connect") {
            val data = message.data<MessageData>() ?: return
            val newTabs = data.tabs
            val oldTabs = messageTabs

            if (newTabs == oldTabs) {
                return
            } else {
                val fragment = bridgeDelegate.destination.fragment
                val activity = fragment.activity as? MainActivity ?: return

                messageTabs = newTabs

                activity.tabsChanged(messageTabs)
            }
        }
    }
}
