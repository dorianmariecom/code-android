package com.codedorian

import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class PathConfigurationJsonTest {
    private val raw = File("src/main/assets/json/path-configuration.json").readText()

    @Test fun acceptsRawAndWrappedConfigurations() {
        val wrapped = """{"status":"ok","messages":[],"data":$raw}"""
        assertEquals(Json.parseToJsonElement(raw), Json.parseToJsonElement(PathConfigurationJson.normalize(wrapped)))
        assertEquals(PathConfigurationJson.normalize(raw), PathConfigurationJson.normalize(wrapped))
    }

    @Test fun preservesRuleOrderAndExceptions() {
        val rules = Json.parseToJsonElement(PathConfigurationJson.normalize(raw)).jsonObject["rules"]!!.jsonArray
        fun properties(path: String) = buildMap {
            for (rule in rules) {
                val value = rule.jsonObject
                if (value["patterns"]!!.jsonArray.any { Regex(it.jsonPrimitive.content).containsMatchIn(path) }) {
                    putAll(value["properties"]!!.jsonObject)
                }
            }
        }
        for (path in listOf("/users/123/edit", "/messages/new")) {
            assertEquals("modal", properties(path)["context"]!!.jsonPrimitive.content)
            assertEquals("hotwire://fragment/web/modal/sheet", properties(path)["uri"]!!.jsonPrimitive.content)
            assertEquals("false", properties(path)["pull_to_refresh_enabled"]!!.jsonPrimitive.content)
        }
        for (path in listOf("/users/new", "/session/new", "/messages")) {
            assertEquals("default", properties(path)["context"]!!.jsonPrimitive.content)
            assertEquals("hotwire://fragment/web", properties(path)["uri"]!!.jsonPrimitive.content)
        }
    }

    @Test(expected = IOException::class) fun rejectsErrorEnvelope() {
        PathConfigurationJson.normalize("""{"status":"error","data":$raw}""")
    }

    @Test(expected = IOException::class) fun rejectsMissingRules() {
        PathConfigurationJson.normalize("""{"status":"ok","data":{}}""")
    }

    @Test(expected = IOException::class) fun rejectsMalformedPatterns() {
        PathConfigurationJson.normalize("""{"rules":[{"patterns":[42],"properties":{}}]}""")
    }
}
