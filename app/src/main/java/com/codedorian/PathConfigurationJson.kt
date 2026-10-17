package com.codedorian

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object PathConfigurationJson {
    fun normalize(source: String): String {
        try {
            val response = Json.parseToJsonElement(source) as? JsonObject
                ?: throw IOException("Expected a configuration object")
            val configuration = if ("data" in response) {
                if ((response["status"] as? JsonPrimitive)?.content != "ok") {
                    throw IOException("Configuration request did not succeed")
                }
                response["data"] as? JsonObject
            } else response
            val rules = configuration?.get("rules") as? JsonArray
                ?: throw IOException("Missing configuration rules")
            for (element in rules) {
                val rule = element as? JsonObject ?: throw IOException("Invalid rule")
                val patterns = rule["patterns"] as? JsonArray ?: throw IOException("Missing patterns")
                if (patterns.any { it !is JsonPrimitive || !it.isString }) {
                    throw IOException("Invalid patterns")
                }
                patterns.forEach { Regex((it as JsonPrimitive).content) }
                if (rule["properties"] !is JsonObject) throw IOException("Missing properties")
            }
            if (configuration["settings"] != null && configuration["settings"] !is JsonObject) {
                throw IOException("Invalid settings")
            }
            return configuration.toString()
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid path configuration", error)
        }
    }
}
