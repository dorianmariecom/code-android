package com.codedorian

import android.app.Application
import com.google.firebase.FirebaseApp
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.KotlinXJsonConverter
import dev.hotwire.core.config.Hotwire
import dev.hotwire.core.turbo.config.PathConfiguration
import dev.hotwire.navigation.config.registerBridgeComponents
import dev.hotwire.navigation.config.registerFragmentDestinations

class CodeDorianApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        Hotwire.loadPathConfiguration(
            context = this,
            location =
                PathConfiguration.Location(
                    remoteFileUrl = AppConfig.configurationsURL,
                ),
        )

        Hotwire.registerFragmentDestinations(
            WebFragment::class,
        )

        Hotwire.registerBridgeComponents(
            BridgeComponentFactory(
                "button",
                ::ButtonComponent,
            ),
            BridgeComponentFactory(
                "notification-token",
                ::NotificationTokenComponent,
            ),
            BridgeComponentFactory(
                "csrf-token",
                ::CsrfTokenComponent,
            ),
            BridgeComponentFactory(
                "tab-bar",
                ::TabBarComponent,
            ),
        )

        Hotwire.config.jsonConverter = KotlinXJsonConverter()
    }
}
