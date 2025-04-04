package com.codedorian

import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class CsrfTokenComponent(
    name: String,
    private val bridgeDelegate: BridgeDelegate<HotwireDestination>
) : BridgeComponent<HotwireDestination>(name, bridgeDelegate) {

    @Serializable
    data class MessageData(
        @SerialName("csrf_token") val csrfToken: String,
    )

    override fun onReceive(message: Message) {
        if (message.event == "connect") {
            val data = message.data<MessageData>() ?: return
            AppConfig.csrfToken = data.csrfToken
        }
    }
}
