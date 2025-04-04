package com.codedorian

import android.util.Log.d

object AppConfig {
    val environment: String = BuildConfig.CODE_ENV

    val baseDomain: String =
        when (environment) {
            "test" -> "http://localhost:3000"
            "localhost" -> "http://localhost:3000"
            "development" -> "https://dev.codedorian.com"
            "staging" -> "https://staging.codedorian.com"
            else -> "https://codedorian.com"
        }

    val baseURL: String = baseDomain
    val configurationsURL: String = "$baseDomain/configurations/android_v1.json"
    val devicesURL: String = "$baseDomain/devices"
    var csrfToken: String? = null
}
