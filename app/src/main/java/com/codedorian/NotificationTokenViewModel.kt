package com.codedorian

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class NotificationTokenViewModel {
    suspend fun registerToken(token: String) =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) return@withContext
            val csrfToken = AppConfig.csrfToken ?: return@withContext

            try {
                val url = URL(AppConfig.devicesURL)

                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json",
                )
                connection.setRequestProperty(
                    "Accept",
                    "application/json",
                )
                connection.setRequestProperty(
                    "X-CSRF-Token",
                    csrfToken,
                )

                CookieManager.getInstance().getCookie(AppConfig.baseURL)?.let {
                    connection.setRequestProperty("Cookie", it)
                }

                val body =
                    JSONObject().apply {
                        put(
                            "device",
                            JSONObject().apply {
                                put("token", token)
                                put("platform", "android")
                            },
                        )
                    }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body.toString())
                }

                connection.responseCode
                connection.disconnect()
            } catch (_: Exception) {
            }
        }
}
