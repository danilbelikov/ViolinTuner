package com.violinjourney.app.core.domain.progress

import kotlin.test.assertEquals
import kotlin.test.Test

class ProfileTest {
    @Test
    fun `a name loses its edge spaces`() {
        assertEquals("Даня", Profile.cleanName("  Даня \n"))
        assertEquals("", Profile.cleanName("   "))
    }

    @Test
    fun `a name is cut at 24 characters and does not end with a space`() {
        assertEquals("а".repeat(24), Profile.cleanName("а".repeat(40)))
        assertEquals("а".repeat(23), Profile.cleanName("а".repeat(23) + " хвост"))
    }

    @Test
    fun `an emoji at the edge of the name is kept whole or left out — never halved`() {
        val violin = "\uD83C\uDFBB" // 🎻
        assertEquals("а".repeat(23) + violin, Profile.cleanName("а".repeat(23) + violin + "хвост"))
        assertEquals("а".repeat(24), Profile.cleanName("а".repeat(24) + violin))
    }
}
