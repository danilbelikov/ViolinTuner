package com.violinjourney.app.feature.sound

import com.violinjourney.app.core.ui.format.FormatLanguage
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.sound_affected_many
import com.violinjourney.app.shared.resources.sound_affected_one
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** «для N записей» on «Звук» takes its word by the rule of the interface language (spec 3.26), not by the Russian one for all. */
class SoundAffectedWordsTest {
    @AfterTest
    fun backToRussian() = Formats.use(FormatLanguage.RUSSIAN)

    private val one = Res.string.sound_affected_one.key
    private val many = Res.string.sound_affected_many.key

    private fun words(count: Int) = affectedWords(count).key

    @Test
    fun `Russian after «для» - 1 and 21 записи and every other count записей`() {
        assertEquals(one, words(1))
        assertEquals(one, words(21))
        listOf(2, 5, 11, 12, 111).forEach { assertEquals(many, words(it), "для $it записей") }
    }

    @Test
    fun `English says recordings for 21 and one recording only for one`() {
        Formats.use(FormatLanguage.ENGLISH)
        assertEquals(one, words(1))
        assertEquals(many, words(21), "for 21 recordings")
        assertEquals(many, words(2))
    }

    @Test
    fun `Japanese has one form`() {
        Formats.use(FormatLanguage.ALL.first { it.tag == "ja" })
        assertEquals(many, words(1))
    }
}
