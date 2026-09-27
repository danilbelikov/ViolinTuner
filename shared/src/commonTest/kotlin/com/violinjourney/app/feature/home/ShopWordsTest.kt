package com.violinjourney.app.feature.home

import com.violinjourney.app.core.ui.format.FormatLanguage
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.arrange_places_few
import com.violinjourney.app.shared.resources.arrange_places_many
import com.violinjourney.app.shared.resources.arrange_places_one
import com.violinjourney.app.shared.resources.shop_sessions_few
import com.violinjourney.app.shared.resources.shop_sessions_many
import com.violinjourney.app.shared.resources.shop_sessions_one_counted
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The counts of the shop and of «Обставить» take the form of the interface language: «21 занятие», «23 места», «24 места». */
class ShopWordsTest {
    @AfterTest
    fun backToRussian() = Formats.use(FormatLanguage.RUSSIAN)

    @Test
    fun `«ещё примерно N занятий» - 21 занятие and 2 занятия and 5 or 11 занятий`() {
        assertEquals(Res.string.shop_sessions_one_counted.key, sessionsLeftWords(21).key)
        assertEquals(Res.string.shop_sessions_few.key, sessionsLeftWords(2).key)
        assertEquals(Res.string.shop_sessions_many.key, sessionsLeftWords(5).key)
        assertEquals(Res.string.shop_sessions_many.key, sessionsLeftWords(11).key)
        Formats.use(FormatLanguage.ENGLISH)
        assertEquals(Res.string.shop_sessions_many.key, sessionsLeftWords(21).key, "about 21 more practices")
    }

    @Test
    fun `the header of «Обставить» - 21 место and 23 места and 25 мест`() {
        assertEquals(Res.string.arrange_places_one.key, placesWords(21).key)
        assertEquals(Res.string.arrange_places_few.key, placesWords(23).key)
        assertEquals(Res.string.arrange_places_few.key, placesWords(4).key)
        assertEquals(Res.string.arrange_places_many.key, placesWords(25).key)
        Formats.use(FormatLanguage.ENGLISH)
        assertEquals(Res.string.arrange_places_one.key, placesWords(1).key)
        assertEquals(Res.string.arrange_places_many.key, placesWords(23).key)
    }
}
