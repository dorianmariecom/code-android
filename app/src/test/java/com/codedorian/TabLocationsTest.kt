package com.codedorian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabLocationsTest {
    private val locations = TabLocations("https://codedorian.com")
    private val roots = listOf("/", "/messages", "/messages/archive", "/people")

    @Test fun selectsLongestMatchingPath() {
        assertEquals(2, locations.matchingIndex("/messages/archive/123?read=true#latest", roots))
        assertEquals(1, locations.matchingIndex("/messages/123", roots))
    }

    @Test fun respectsPathBoundaries() {
        assertEquals(0, locations.matchingIndex("/messages-other", roots))
        assertEquals(-1, locations.matchingIndex("/messages-other", listOf("/messages")))
        assertEquals(1, locations.matchingIndex("/messages/", roots))
    }

    @Test fun externalOriginsDoNotSelectAnAppTab() {
        listOf("https://other.com/messages", "http://codedorian.com/messages",
            "https://codedorian.com:8443/messages", "https://codedorian.com@other.com/messages")
            .forEach { assertEquals(-1, locations.matchingIndex(it, roots)) }
        assertEquals(1, locations.matchingIndex("https://codedorian.com:443/messages", roots))
    }

    @Test fun resolvesRelativeNotificationPaths() {
        assertEquals("https://codedorian.com/messages/123", locations.resolve(" messages/123 "))
        assertEquals("https://codedorian.com/messages/123", locations.resolve("/messages/123"))
    }

    @Test fun rejectsMalformedAndNonWebLocations() {
        listOf("mailto:a@example.com", "javascript:alert(1)", "tel:123", "http://[", "/bad path")
            .forEach { assertNull(locations.resolve(it)) }
    }

    @Test fun queryTabsMatchWithoutLosingQueriesInScrollKeys() {
        val tabs = listOf("/messages", "/messages?filter=unread")
        assertEquals(1, locations.matchingIndex("/messages?filter=unread#latest", tabs))
        assertEquals(0, locations.matchingIndex("/messages?filter=all", tabs))
        assertEquals("https://codedorian.com/messages?filter=unread", locations.key("/messages?filter=unread#latest"))
    }
}
