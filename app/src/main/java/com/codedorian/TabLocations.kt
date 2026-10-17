package com.codedorian

import java.net.URI

/** URL matching shared by tab clicks, web navigation, and notification links. */
internal class TabLocations(private val baseUrl: String) {
    fun resolve(path: String): String? = runCatching {
        val uri = URI(baseUrl.trimEnd('/') + "/").resolve(path.trim())
        uri.takeIf { it.scheme in listOf("http", "https") && it.host != null }?.toString()
    }.getOrNull()

    fun key(location: String): String = resolve(location)?.substringBefore('#') ?: location

    fun matchingIndex(location: String, roots: List<String>): Int {
        val target = resolve(location)?.let { URI(it) } ?: return -1
        return roots.mapIndexedNotNull { index, root ->
            val candidate = resolve(root)?.let { URI(it) } ?: return@mapIndexedNotNull null
            val path = candidate.path.orEmpty().trimEnd('/')
            val targetPath = target.path.orEmpty().trimEnd('/')
            if (candidate.scheme != target.scheme || !candidate.host.equals(target.host, ignoreCase = true) ||
                port(candidate) != port(target) ||
                !(targetPath == path || targetPath.startsWith("$path/")) ||
                (candidate.rawQuery != null && candidate.rawQuery != target.rawQuery)
            ) return@mapIndexedNotNull null
            index to (path.length * 2 + if (candidate.rawQuery != null) 1 else 0)
        }.maxByOrNull { it.second }?.first ?: -1
    }

    private fun port(uri: URI): Int = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80
}
