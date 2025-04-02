package com.codedorian

import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class NotificationTokenViewModel {
    suspend fun registerToken(token: String) = withContext(Dispatchers.IO) {
        try {
            val url = URL(AppConfig.devicesURL)

            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            CookieManager.getInstance().getCookie(AppConfig.baseURL)?.let {
                connection.setRequestProperty("Cookie", it)
            }

            val body = JSONObject().apply {
                put("token", token)
                put("platform", "android")
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
            }

            connection.responseCode
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


