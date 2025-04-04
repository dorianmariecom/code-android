package com.codedorian

import android.util.Log.d
import com.google.firebase.FirebaseApp
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination

class NotificationTokenComponent(
    name: String,
    private val bridgeDelegate: BridgeDelegate<HotwireDestination>
) : BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    private val fragment: WebFragment
        get() = bridgeDelegate.destination.fragment as WebFragment

    override fun onReceive(message: Message) {
        if (message.event == "connect") {
            fragment.requestNotificationPermission()
        }
    }
}

