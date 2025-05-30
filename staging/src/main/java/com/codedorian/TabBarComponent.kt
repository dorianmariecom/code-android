package com.codedorian

import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
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
            val fragment = bridgeDelegate.destination.fragment as? WebFragment ?: return
            val activity = fragment.activity as? MainActivity ?: return
            val data = message.data<MessageData>() ?: return
            val newTabs = data.tabs
            val oldTabs = AppConfig.tabs

            if (newTabs == oldTabs) {
                return
            } else {
                AppConfig.tabs = newTabs
                activity.tabsChanged()
            }
        }
    }
}
