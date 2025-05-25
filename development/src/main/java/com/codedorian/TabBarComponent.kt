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
    @Serializable
    data class MessageData(
        @SerialName("tabs") val tabs: List<Tab>,
    )

    override fun onReceive(message: Message) {
        if (message.event == "connect") {
            val data = message.data<MessageData>() ?: return
            val newTabs = data.tabs
            val oldTabs = AppConfig.tabs

            if (newTabs == oldTabs) {
                return
            } else {
                AppConfig.tabs = newTabs
2
                val fragment = bridgeDelegate.destination.fragment
                val activity = fragment.activity as? MainActivity ?: return
                activity.tabsChanged()
            }
        }
    }
}
