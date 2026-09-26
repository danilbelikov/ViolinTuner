package com.violinjourney.app.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The audio session of iOS is one for the app: only the last one using it lets it go. */
class SessionUsersTest {
    private var letGo = 0
    private val users = SessionUsers { letGo++ }

    @Test
    fun `the last one out lets the session go once`() {
        users.enter { }
        users.leave()
        assertEquals(1, letGo)
    }

    @Test
    fun `nobody lets it go while another is still in — a microphone leaving under a player`() {
        users.enter { } // the microphone of a Live still listening
        users.enter { } // a player started meanwhile
        users.leave() // the Live stops listening
        assertEquals(0, letGo, "the player still plays")
        users.leave() // the player is let go
        assertEquals(1, letGo)
    }

    @Test
    fun `an opening that throws is not counted`() {
        assertFailsWith<IllegalStateException> { users.enter { error("the session would not open") } }
        users.enter { }
        users.leave()
        assertEquals(1, letGo, "the failed opening held nothing")
    }

    @Test
    fun `after letting go a new user counts from nothing`() {
        users.enter { }
        users.leave()
        users.enter { }
        users.enter { }
        users.leave()
        assertEquals(1, letGo)
        users.leave()
        assertEquals(2, letGo)
    }

    @Test
    fun `a leave with nobody in lets nothing go`() {
        users.leave()
        assertEquals(0, letGo)
        users.enter { }
        users.leave()
        users.leave()
        assertEquals(1, letGo)
    }

    @Test
    fun `the opening gives back what it made`() {
        assertEquals(42, users.enter { 42 })
    }
}
