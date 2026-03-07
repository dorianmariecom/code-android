package com.codedorian

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
    val configurationsURL: String = "$baseDomain/configurations/android.json"
    val devicesURL: String = "$baseDomain/devices"
    var csrfToken: String? = null
    var tabs: List<Tab> = emptyList()
    val buttonButtonId = 1001
    val menuViewId = 1002
    val refreshButtonId = 1003
    val shareButtonId = 1004
    val searchViewId = 1005
    val searchDelay = 20L
    val refreshDelay = 40L
    val refreshAnimationDuration = 900L
    val shareDelay = 60L
}
