package com.violinjourney.app.core.ui.format

import com.violinjourney.app.core.domain.repertoire.PieceGroup
import com.violinjourney.app.core.domain.repertoire.SectionStats
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Names go by the alphabet of the language of the interface (spec 3.22 «свои по алфавиту»), the platform's: the JDK
 * in these tests on the JVM, Foundation on iOS, ICU on a phone. Only letters that differ in the alphabet itself are
 * compared here — where case and accents break a tie, and where one script stands against another, the platforms may
 * differ, and nothing is promised.
 */
class AlphabeticalTest {
    @AfterTest
    fun backToRussian() = Formats.use(FormatLanguage.RUSSIAN)

    private fun sorted(tag: String, vararg names: String): List<String> {
        Formats.use(FormatLanguage.ALL.first { it.tag == tag })
        return names.sortedWith(Formats.alphabetical())
    }

    @Test
    fun `the Russian yo stands with ye before ya — and the case does not lead`() {
        assertEquals(listOf("двойки", "Ёлочные", "Январь"), sorted("ru", "Январь", "Ёлочные", "двойки"))
        assertEquals(listOf("Елка", "жук"), sorted("ru", "жук", "Елка"))
    }

    @Test
    fun `accented letters stand with their letter — and a small letter is not after the capitals`() {
        assertEquals(listOf("Äpfel", "Birne", "Zebra"), sorted("de", "Zebra", "Äpfel", "Birne"))
        assertEquals(listOf("École", "fête", "zoo"), sorted("fr", "zoo", "École", "fête"))
        assertEquals(listOf("apple", "Banana", "cherry"), sorted("en", "cherry", "Banana", "apple"))
    }

    @Test
    fun `the player's own sections follow the alphabet — the codes of the letters would put yo after ya`() {
        val groups = listOf(PieceGroup(1, "Январь", 1), PieceGroup(2, "Ёлочные", 2), PieceGroup(3, "двойки", 3))
        fun own(byName: Comparator<String>) = SectionStats.summaries(emptyList(), groups, byName).mapNotNull { it.name }
        assertEquals(listOf("двойки", "Январь", "Ёлочные"), own(SectionStats.LOWERCASE_ORDER))
        assertEquals(listOf("двойки", "Ёлочные", "Январь"), own(Formats.alphabetical()))
    }
}
