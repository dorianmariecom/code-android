@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.codedorian

import android.content.Context
import dev.hotwire.core.config.Hotwire
import dev.hotwire.core.turbo.config.PathConfiguration
import dev.hotwire.core.turbo.http.HotwireHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody

object AppPathConfiguration {
    fun load(context: Context) {
        // Hotwire 1.3.2 has no public response adapter. Keep this integration
        // isolated and initialize its cache before installing the interceptor,
        // so creating the first Session cannot replace the adapted HTTP client.
        HotwireHttpClient.enableCachingWith(context)
        HotwireHttpClient.instance = HotwireHttpClient.instance.newBuilder()
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (chain.request().url.toString() != AppConfig.configurationsURL || !response.isSuccessful) {
                    response
                } else {
                    response.use {
                        val contentType = it.body.contentType()
                        val normalized = PathConfigurationJson.normalize(it.body.string())
                        it.newBuilder()
                            .removeHeader("Content-Length")
                            .body(normalized.toResponseBody(contentType))
                            .build()
                    }
                }
            }
            .build()

        // Older app versions cached the envelope as an empty configuration.
        val preferences = context.getSharedPreferences("path_configuration", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("envelope_support", false)) {
            context.getSharedPreferences("turbo", Context.MODE_PRIVATE).edit()
                .remove(AppConfig.configurationsURL).apply()
            preferences.edit().putBoolean("envelope_support", true).apply()
        }

        Hotwire.loadPathConfiguration(
            context = context,
            location = PathConfiguration.Location(
                assetFilePath = "json/path-configuration.json",
                remoteFileUrl = AppConfig.configurationsURL,
            ),
        )
    }
}
