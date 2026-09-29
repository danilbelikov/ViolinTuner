package com.violinjourney.app.navigation

import com.violinjourney.app.core.analytics.screenKeyOf
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.feature.backup.RestoreViewModel
import com.violinjourney.app.feature.camera.CaptureViewModel
import com.violinjourney.app.feature.journey.StopViewModel
import com.violinjourney.app.feature.repertoire.RepertoireViewModel
import com.violinjourney.app.feature.repertoire.SectionKeys
import com.violinjourney.app.feature.repertoire.form.PieceFormViewModel
import com.violinjourney.app.feature.repertoire.piece.PieceViewModel
import com.violinjourney.app.feature.repertoire.scale.ScaleFormViewModel
import com.violinjourney.app.feature.repertoire.stand.StandViewModel
import com.violinjourney.app.feature.session.SessionViewModel
import com.violinjourney.app.feature.sound.SoundViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RoutesTest {
    /** [pattern] with each `{name}` put in its value, as navigation matches a route against it. */
    private fun fill(pattern: String, vararg values: Pair<String, Any>): String =
        values.fold(pattern) { route, (name, value) -> route.replace("{$name}", value.toString()) }.also {
            assertFalse('{' in it, "a pattern argument left unfilled in $it")
        }

    @Test
    fun `every route built for a screen fills its pattern`() {
        assertEquals(fill(Routes.SESSION_PATTERN, SessionViewModel.ARG_SESSION_ID to 10), Routes.session(10))
        assertEquals(fill(Routes.SOUND_PATTERN, SoundViewModel.ARG_SESSION_ID to SoundViewModel.EVERYONE), Routes.sound(null))
        assertEquals(fill(Routes.SOUND_PATTERN, SoundViewModel.ARG_SESSION_ID to 7), Routes.sound(7))
        assertEquals(fill(Routes.PIECE_PATTERN, PieceViewModel.ARG_PIECE_ID to 51), Routes.piece(51))
        assertEquals(fill(Routes.CAPTURE_PATTERN, CaptureViewModel.ARG_PIECE_ID to 4), Routes.capture(4))
        assertEquals(fill(Routes.STAND_PATTERN, StandViewModel.ARG_PIECE_ID to 3, StandViewModel.ARG_PAGE to 2), Routes.stand(3, 2))
        assertEquals(
            fill(
                Routes.PIECE_FORM_PATTERN,
                PieceFormViewModel.ARG_PIECE_ID to PieceFormViewModel.NEW_PIECE,
                PieceFormViewModel.ARG_FOCUS_NOTES to false,
                PieceFormViewModel.ARG_SECTION to SectionKeys.keyOf(SectionRef.BuiltIn(PieceSection.PIECES)),
            ),
            Routes.pieceForm(null),
        )
        assertEquals(
            fill(
                Routes.PIECE_FORM_PATTERN,
                PieceFormViewModel.ARG_PIECE_ID to 5,
                PieceFormViewModel.ARG_FOCUS_NOTES to true,
                PieceFormViewModel.ARG_SECTION to SectionKeys.keyOf(SectionRef.Custom(9)),
            ),
            Routes.pieceForm(5, focusNotes = true, section = SectionRef.Custom(9)),
        )
        assertEquals(
            fill(Routes.SCALE_FORM_PATTERN, ScaleFormViewModel.ARG_PIECE_ID to ScaleFormViewModel.NEW_SCALE, ScaleFormViewModel.ARG_FOCUS_NOTES to false),
            Routes.scaleForm(null),
        )
        assertEquals(fill(Routes.SCALE_FORM_PATTERN, ScaleFormViewModel.ARG_PIECE_ID to 8, ScaleFormViewModel.ARG_FOCUS_NOTES to false), Routes.scaleForm(8))
        // «Добавить заметку» of a scale opens its form at the notes, as a piece's does (spec 3.15, 3.36.4)
        assertEquals(
            fill(Routes.SCALE_FORM_PATTERN, ScaleFormViewModel.ARG_PIECE_ID to 8, ScaleFormViewModel.ARG_FOCUS_NOTES to true),
            Routes.scaleForm(8, focusNotes = true),
        )
        assertEquals(
            fill(Routes.SECTION_PATTERN, RepertoireViewModel.ARG_SECTION to SectionKeys.keyOf(SectionRef.BuiltIn(PieceSection.SCALES))),
            Routes.section(SectionRef.BuiltIn(PieceSection.SCALES)),
        )
        assertEquals(fill(Routes.JOURNEY_STOP_PATTERN, StopViewModel.ARG_STOP_ID to "vienna"), Routes.stop("vienna"))
    }

    @Test
    fun `a new element of the scales opens the form of a scale and of every other section the form of a piece with it`() {
        assertEquals(Routes.scaleForm(null), Routes.newElement(SectionRef.BuiltIn(PieceSection.SCALES)))
        for (section in listOf(PieceSection.PIECES, PieceSection.ETUDES, PieceSection.STROKES)) {
            assertEquals(Routes.pieceForm(null, section = SectionRef.BuiltIn(section)), Routes.newElement(SectionRef.BuiltIn(section)), "$section")
        }
        assertEquals(Routes.pieceForm(null, section = SectionRef.Custom(9)), Routes.newElement(SectionRef.Custom(9)))
    }

    @Test
    fun `the names of the screens stay what the statistics know`() {
        val routes = listOf(
            Routes.SESSION_PATTERN, Routes.SOUND_PATTERN, Routes.PIECE_PATTERN, Routes.CAPTURE_PATTERN, Routes.STAND_PATTERN,
            Routes.PIECE_FORM_PATTERN, Routes.SCALE_FORM_PATTERN, Routes.SECTION_PATTERN, Routes.JOURNEY, Routes.JOURNEY_MAP,
            Routes.JOURNEY_PASSPORT, Routes.JOURNEY_STOP_PATTERN, Routes.HOME, Routes.HOME_SHOP, Routes.HOME_ARRANGE, Routes.HOME_HOUSES,
            Routes.SPLASH_AWAY, Routes.SPLASH_HOME, Routes.SETTINGS, Routes.BACKUP, Routes.RESTORE_PATTERN,
        )
        // the keys of screen_open (spec 5.27): a new name here is a new screen in the statistics, on both platforms
        val keys = listOf(
            "session", "sound", "piece", "capture", "stand", "pieceForm", "scaleForm", "section", "journey", "journeyMap",
            "journeyPassport", "journeyStop", "home", "homeShop", "homeArrange", "homeHouses", "splashAway", "splashHome",
            "settings", "backup", "restore",
        )
        assertEquals(keys, routes.map(::screenKeyOf))
        assertEquals(Routes.SPLASH_HOME, Routes.stop(JourneyRoute.HOME), "the home of the journey opens through its title card")
    }

    @Test
    fun `a copy's address comes back whole through the restore route`() {
        val addresses = listOf(
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2FКопия 2026-09-27.zip",
            "file:///private/var/mobile/Containers/Shared (1)/Копия #2 & ещё?.zip",
        )
        addresses.forEach { uri ->
            val route = Routes.restore(uri)
            val prefix = "${Routes.RESTORE}?${RestoreViewModel.ARG_URI}="
            assertEquals(prefix, route.take(prefix.length))
            val value = route.drop(prefix.length)
            listOf('&', '=', '?', '#', '/', ' ').forEach { raw -> assertFalse(raw in value, "a raw '$raw' in $value") }
            assertEquals(uri, percentDecoded(value))
        }
        assertEquals(Routes.RESTORE, Routes.restore(""))
    }

    private fun percentDecoded(value: String): String {
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i < value.length) {
            if (value[i] == '%') {
                bytes += value.substring(i + 1, i + 3).toInt(16).toByte()
                i += 3
            } else {
                bytes += value[i].code.toByte()
                i++
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}
