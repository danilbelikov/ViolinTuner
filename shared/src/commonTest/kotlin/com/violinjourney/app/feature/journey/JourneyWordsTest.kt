package com.violinjourney.app.feature.journey

import com.violinjourney.app.core.ui.format.FormatLanguage
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.journey_sessions_few
import com.violinjourney.app.shared.resources.journey_sessions_many
import com.violinjourney.app.shared.resources.journey_sessions_one
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** «примерно N занятий» (spec 3.36.7, 5.18) takes the form of the language of the interface: «1 занятие», «2 занятия», «5 занятий». */
class JourneyWordsTest {
    @AfterTest
    fun backToRussian() = Formats.use(FormatLanguage.RUSSIAN)

    private val one = Res.string.journey_sessions_one.key
    private val few = Res.string.journey_sessions_few.key
    private val many = Res.string.journey_sessions_many.key

    @Test
    fun `in Russian - 1 and 21 занятие - 2 4 and 22 занятия - 5 11 14 and 25 занятий`() {
        listOf(1, 21).forEach { assertEquals(one, sessionsWords(it).key, "$it") }
        listOf(2, 4, 22).forEach { assertEquals(few, sessionsWords(it).key, "$it") }
        listOf(5, 11, 14, 25).forEach { assertEquals(many, sessionsWords(it).key, "$it") }
    }

    @Test
    fun `in English one practice and two practices - in French one séance - in Japanese one form`() {
        Formats.use(FormatLanguage.ENGLISH)
        assertEquals(one, sessionsWords(1).key)
        assertEquals(many, sessionsWords(2).key)
        Formats.use(FormatLanguage.of("fr"))
        assertEquals(one, sessionsWords(1).key)
        Formats.use(FormatLanguage.of("ja"))
        assertEquals(many, sessionsWords(1).key, "Japanese has one form")
    }
}
