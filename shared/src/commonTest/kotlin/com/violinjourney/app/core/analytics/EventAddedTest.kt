package com.violinjourney.app.core.analytics

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.Repeat
import kotlin.test.Test
import kotlin.test.assertEquals

class EventAddedTest {
    @Test
    fun `a built-in kind goes by its key from the spec and the repeat by its word`() {
        assertEquals(mapOf("kind" to "lesson", "repeat" to "weekly"), EventAdded(KindRef.BuiltIn(BuiltInKind.LESSON), Repeat.WEEKLY).params)
        assertEquals(mapOf("kind" to "rehearsal", "repeat" to "biweekly"), EventAdded(KindRef.BuiltIn(BuiltInKind.REHEARSAL), Repeat.BIWEEKLY).params)
        assertEquals(mapOf("kind" to "performance", "repeat" to "none"), EventAdded(KindRef.BuiltIn(BuiltInKind.PERFORMANCE), Repeat.NONE).params)
        assertEquals("other", EventAdded(KindRef.BuiltIn(BuiltInKind.OTHER), Repeat.NONE).params["kind"])
        assertEquals("event_added", EventAdded(KindRef.BuiltIn(BuiltInKind.OTHER), Repeat.NONE).name)
    }

    @Test
    fun `a kind of the player's own is custom - its name never leaves the phone`() {
        assertEquals(mapOf("kind" to "custom", "repeat" to "none"), EventAdded(KindRef.Custom(42), Repeat.NONE).params)
    }
}
