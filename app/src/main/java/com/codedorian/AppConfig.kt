package com.codedorian

object AppConfig {
    val environment: String = when {
        BuildConfig.CODE_ENV_TEST -> "test"
        BuildConfig.CODE_ENV_LOCALHOST -> "localhost"
        BuildConfig.CODE_ENV_DEVELOPMENT -> "development"
        BuildConfig.CODE_ENV_STAGING -> "staging"
        else -> "production"
    }

    val baseDomain: String = when (environment) {
        "test" -> "http://localhost:3000"
        "localhost" -> "http://localhost:3000"
        "development" -> "https://dev.codedorian.com"
        "staging" -> "https://staging.codedorian.com"
        else -> "https://codedorian.com"
    }

    val baseURL: String = baseDomain
    val configurationsURL: String = "$baseDomain/configurations/ios_v1.json"
    val devicesURL: String = "$baseDomain/devices"
    var csrfToken: String? = null
}