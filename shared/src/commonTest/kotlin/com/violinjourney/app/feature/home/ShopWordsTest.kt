package com.violinjourney.app.feature.home

import com.violinjourney.app.core.ui.format.FormatLanguage
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.arrange_places_few
import com.violinjourney.app.shared.resources.arrange_places_many
import com.violinjourney.app.shared.resources.arrange_places_one
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The count of «Обставить» takes the form of the interface language: «21 место», «23 места», «25 мест». The practices of «примерно N
 * занятий» of the card of a thing are the journey's words since R7 (`JourneyWordsTest`).
 */
class ShopWordsTest {
    @AfterTest
    fun backToRussian() = Formats.use(FormatLanguage.RUSSIAN)

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
