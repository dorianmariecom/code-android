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
            val data = message.data<MessageData>() ?: return
            val newTabs = data.tabs
            val allTabs = Tab.all

            if (newTabs == allTabs) {
                return
            } else {
                Tab.all = newTabs
                val activity = bridgeDelegate.destination.fragment.activity as? MainActivity ?: return
                activity.onTabsChanged()
            }
        }
    }
}
