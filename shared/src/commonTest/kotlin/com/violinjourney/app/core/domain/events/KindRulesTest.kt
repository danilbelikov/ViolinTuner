package com.violinjourney.app.core.domain.events

import com.violinjourney.app.core.domain.events.TestEvents.LESSON
import com.violinjourney.app.core.domain.events.TestEvents.OTHER
import com.violinjourney.app.core.domain.events.TestEvents.PERFORMANCE
import com.violinjourney.app.core.domain.events.TestEvents.REHEARSAL
import com.violinjourney.app.core.ui.format.FormatLanguage
import com.violinjourney.app.core.ui.format.Formats
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KindRulesTest {
    private val config = EventsConfig()

    @AfterTest
    fun backToRussian() = Formats.use(FormatLanguage.RUSSIAN)

    private fun own(id: Long, name: String, color: Int = 1, sign: KindSign = KindSign.BOOK, createdAt: Long = id) =
        StoredKind.Own(id, name, color, sign, createdAt)

    @Test
    fun `the four built-in kinds are always there with their colours and the own ones follow by creation`() {
        val kinds = KindRules.all(listOf(own(9, "Оркестр", createdAt = 20), own(4, "Сольфеджио", createdAt = 10)), config)
        assertEquals(listOf(LESSON, REHEARSAL, PERFORMANCE, OTHER, KindRef.Custom(4), KindRef.Custom(9)), kinds.map { it.ref })
        // Синий, Бирюза, Роза, Лайм (spec 5.29 R9)
        assertEquals(listOf(0, 5, 2, 7), kinds.take(4).map { it.look.color })
        assertEquals(listOf(KindSign.LESSON, KindSign.REHEARSAL, KindSign.PERFORMANCE, KindSign.OTHER), kinds.take(4).map { it.look.sign })
        assertEquals(listOf(null, null, null, null, "Сольфеджио", "Оркестр"), kinds.map { it.ownName })
    }

    @Test
    fun `a built-in kind given a colour keeps it and a colour out of the set reads as the default`() {
        val kinds = KindRules.all(
            listOf(StoredKind.Recolor(BuiltInKind.LESSON, 3), StoredKind.Recolor(BuiltInKind.PERFORMANCE, 99), own(5, "Оркестр", color = -1)),
            config,
        )
        assertEquals(3, KindRules.lookOf(LESSON, kinds, config).color)
        assertEquals(2, KindRules.lookOf(PERFORMANCE, kinds, config).color, "a colour this build does not have")
        assertEquals(7, KindRules.lookOf(KindRef.Custom(5), kinds, config).color, "one's own out of the set — the colour of «Другое»")
        assertEquals(5, KindRules.lookOf(REHEARSAL, kinds, config).color)
    }

    @Test
    fun `a kind of ones own that is gone looks as other`() {
        val kinds = KindRules.all(listOf(StoredKind.Recolor(BuiltInKind.OTHER, 4)), config)
        assertEquals(KindLook(KindSign.OTHER, 4), KindRules.lookOf(KindRef.Custom(77), kinds, config))
        assertEquals(OTHER, KindRules.kindOf(KindRef.Custom(77), kinds, config).ref)
        assertEquals(OTHER, KindRules.resolve(KindRef.Custom(77), kinds))
        assertEquals(EventName.OfKind(OTHER, null), EventName.of(" ", KindRef.Custom(77), kinds))
    }

    @Test
    fun `kinds not read yet still give the built-in ones their look`() {
        assertEquals(KindLook(KindSign.PERFORMANCE, 2), KindRules.lookOf(PERFORMANCE, emptyList(), config))
        assertEquals(KindLook(KindSign.OTHER, 7), KindRules.lookOf(KindRef.Custom(3), emptyList(), config))
        assertEquals(LESSON, KindRules.resolve(LESSON, emptyList()))
        assertEquals(EventName.OfKind(LESSON, null), EventName.of("", LESSON, emptyList()))
    }

    @Test
    fun `the form shows the built-in kinds first and then the own ones by the alphabet of the interface`() {
        Formats.use(FormatLanguage.RUSSIAN)
        val kinds = KindRules.all(listOf(own(1, "Сольфеджио"), own(2, "Оркестр"), own(3, "Мастер-класс")), config)
        assertEquals(
            listOf(LESSON, REHEARSAL, PERFORMANCE, OTHER, KindRef.Custom(3), KindRef.Custom(2), KindRef.Custom(1)),
            KindRules.ordered(kinds, Formats.alphabetical()).map { it.ref },
        )
        // the alphabet, not the codes of the letters: «Ё» stands with «Е», before «Я»
        val yo = KindRules.all(listOf(own(1, "Ярмарка"), own(2, "Ёлка"), own(3, "Дуэт")), config)
        assertEquals(listOf("Дуэт", "Ёлка", "Ярмарка"), KindRules.ordered(yo, Formats.alphabetical()).mapNotNull { it.ownName })
        assertEquals(listOf("Дуэт", "Ярмарка", "Ёлка"), KindRules.ordered(yo).mapNotNull { it.ownName }, "the codes put «ё» after «я»")
    }

    @Test
    fun `a name is needed and no other kind may have it whatever the case`() {
        val shown = mapOf(LESSON to "Урок", REHEARSAL to "Репетиция", PERFORMANCE to "Выступление", OTHER to "Другое", KindRef.Custom(5) to "Оркестр")
        assertEquals(KindNameProblem.Taken("Урок"), KindRules.nameProblem("  урок ", editedId = null, shown, config))
        assertEquals(KindNameProblem.Taken("Оркестр"), KindRules.nameProblem("ОРКЕСТР", editedId = null, shown, config))
        assertNull(KindRules.nameProblem("Оркестр", editedId = 5, shown, config), "its own name, when it is edited")
        assertNull(KindRules.nameProblem("оркестр ", editedId = 5, shown, config))
        assertEquals(KindNameProblem.Empty, KindRules.nameProblem("", editedId = null, shown, config))
        assertEquals(KindNameProblem.Empty, KindRules.nameProblem("   ", editedId = null, shown, config))
        assertNull(KindRules.nameProblem("Сольфеджио", editedId = null, shown, config))
    }

    @Test
    fun `a new kind takes the first colour and sign nobody has`() {
        // the built-in kinds hold 0, 5, 2 and 7
        assertEquals(KindLook(KindSign.BOOK, 1), KindRules.firstFree(KindRules.all(emptyList(), config), config))
        val two = KindRules.all(listOf(own(1, "А", color = 1, sign = KindSign.BOOK), own(2, "Б", color = 3, sign = KindSign.KEYS)), config)
        assertEquals(KindLook(KindSign.HAT, 4), KindRules.firstFree(two, config))
    }

    @Test
    fun `with every colour and sign taken the one fewest kinds have comes first`() {
        // colours: the built-in 0 5 2 7, then own 1 3 4 6 — all eight once; then 0 and 1 a second time
        val colors = listOf(1, 3, 4, 6, 0, 1)
        val signs = KindSign.OWN + KindSign.BOOK
        val stored = colors.mapIndexed { index, color -> own(index + 1L, "Вид $index", color, signs[index]) } +
            (colors.size until signs.size).map { index -> own(index + 1L, "Вид $index", color = 2, sign = signs[index]) }
        val look = KindRules.firstFree(KindRules.all(stored, config), config)
        assertEquals(3, look.color, "0, 1 and 2 are taken twice or more; 3 is the smallest of those taken once")
        assertEquals(KindSign.HAT, look.sign, "the book is taken twice; the hat is the first of those taken once")
    }

    @Test
    fun `a sign taken by another kind says whose it is`() {
        val kinds = KindRules.all(
            listOf(own(1, "Оркестр", sign = KindSign.ARC, createdAt = 10), own(2, "Ансамбль", sign = KindSign.ARC, createdAt = 20), own(3, "Сольфеджио")),
            config,
        )
        assertEquals(mapOf(KindSign.ARC to "Оркестр", KindSign.BOOK to "Сольфеджио"), KindRules.takenSigns(kinds, exceptId = null))
        // the kind being edited does not take its own sign from itself
        assertEquals(mapOf(KindSign.ARC to "Ансамбль", KindSign.BOOK to "Сольфеджио"), KindRules.takenSigns(kinds, exceptId = 1))
        assertEquals(mapOf(KindSign.ARC to "Оркестр"), KindRules.takenSigns(kinds, exceptId = 3))
    }

    @Test
    fun `the twentieth kind of ones own is the last`() {
        val nineteen = KindRules.all((1L..19L).map { own(it, "Вид $it") }, config)
        assertTrue(KindRules.canAddOwn(nineteen, config))
        val twenty = KindRules.all((1L..20L).map { own(it, "Вид $it") }, config)
        assertFalse(KindRules.canAddOwn(twenty, config))
    }
}
