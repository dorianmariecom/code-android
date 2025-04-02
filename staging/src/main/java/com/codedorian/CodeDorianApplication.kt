package com.codedorian

import android.app.Application
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.KotlinXJsonConverter
import dev.hotwire.core.config.Hotwire
import dev.hotwire.core.turbo.config.PathConfiguration
import dev.hotwire.navigation.config.registerBridgeComponents
import dev.hotwire.navigation.config.registerFragmentDestinations
import dev.hotwire.navigation.fragments.HotwireWebFragment

class CodeDorianApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Hotwire.loadPathConfiguration(
            context = this,
            location =
                PathConfiguration.Location(
                    remoteFileUrl = AppConfig.configurationsURL,
                ),
        )

        Hotwire.registerFragmentDestinations(
            HotwireWebFragment::class,
        )

        Hotwire.registerBridgeComponents(
            BridgeComponentFactory("button", ::ButtonComponent),
        )

        Hotwire.config.jsonConverter = KotlinXJsonConverter()
    }
}
