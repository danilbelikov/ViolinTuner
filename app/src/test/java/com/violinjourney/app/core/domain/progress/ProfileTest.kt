package com.violinjourney.app.core.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
