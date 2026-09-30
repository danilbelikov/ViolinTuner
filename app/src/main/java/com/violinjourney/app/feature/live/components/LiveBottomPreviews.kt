package com.violinjourney.app.feature.live.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.RecordingBar
import com.violinjourney.app.core.domain.session.RecordingRibbon
import com.violinjourney.app.feature.live.LiveLayoutMath
import com.violinjourney.app.feature.live.RecordingState
import com.violinjourney.app.feature.live.block.BlockBookmark
import com.violinjourney.app.feature.live.block.Bookmark

// The bottom row of Live one by one (spec 3.36.6, 5.29 R6): the two cards of one form, the record key, the strip of a take and the card
// «нет разрешения» — over a picture, a dark room or a light hall, as they stand on Live.

private val Lit: () -> Float = { 1f }
private val Dark: () -> Float = { 0.38f }

private const val CONCERTO = "Концерт ля минор, I ч."
private const val LONG_NAME = "Концерт ми минор, соч. 64, I. Allegro molto appassionato"
private const val PRACTICE_MS = 24 * 60_000L + 18_000L

/** The bottom row as Live lays it upright: [bookmark] · the key · the practice tag of [practiceMs], the cards as wide as the row allows. */
@Composable
private fun BottomRow(
    bookmark: Bookmark,
    practiceMs: Long?,
    light: () -> Float = Lit,
    recording: Boolean = false,
    keyEnabled: Boolean = true,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = LiveDimens.KeyRowPadding)) {
        val card: Dp = LiveLayoutMath.keyCardWidth(maxWidth.value).dp
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LiveDimens.KeyRowSide),
            horizontalArrangement = Arrangement.spacedBy(LiveDimens.CardToKey, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(card)) { BlockBookmark(bookmark, card, onClick = {}, light = light) }
            LiveRecordKey(recording = recording, enabled = keyEnabled, onClick = {}, alpha = { RecordKeyLight.alpha(recording, keyEnabled, light()) })
            Box(Modifier.width(card)) { PracticeTag(practiceMs, card, onClick = {}, light = light) }
        }
    }
}

@Preview(name = "Bottom row · no practice: «Что играю · выбрать», the key, «Начать · занятие» on glass", widthDp = 412, heightDp = 300, locale = "ru")
@Composable
private fun IdleRowPreview() = OverPicture {
    BottomRow(Bookmark.Entry, practiceMs = null)
    Box(Modifier.fillMaxWidth()) {
        Picture(light = true, Modifier.matchParentSize())
        BottomRow(Bookmark.Entry, practiceMs = null)
    }
}

@Preview(name = "Bottom row · a block and a practice run: paper, «ещё 7 мин» and the brass line, the velvet dot and the time", widthDp = 412, heightDp = 300, locale = "ru")
@Composable
private fun RunningRowPreview() = OverPicture {
    BottomRow(Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.53f), practiceMs = PRACTICE_MS)
    BottomRow(Bookmark.Running(LONG_NAME, minutesLeft = 12, progress = 0.2f), practiceMs = 83 * 60_000L + 5_000L)
}

@Preview(name = "Bottom row · «готово»: the brass rim, the check in ink — whole while the light is out", widthDp = 412, heightDp = 300, locale = "ru")
@Composable
private fun DoneRowPreview() = OverPicture {
    BottomRow(Bookmark.Done(CONCERTO), practiceMs = PRACTICE_MS)
    BottomRow(Bookmark.Done(CONCERTO), practiceMs = PRACTICE_MS, light = Dark)
}

@Preview(name = "Bottom row · the light out: the cards and the key at rest 0.38; recording: the key whole with its stop", widthDp = 412, heightDp = 300, locale = "ru")
@Composable
private fun DimmedRowPreview() = OverPicture {
    BottomRow(Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.53f), practiceMs = PRACTICE_MS, light = Dark)
    BottomRow(Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.53f), practiceMs = PRACTICE_MS, light = Dark, recording = true)
}

@Preview(name = "Bottom row · «Настройка» or no microphone: the key 0.4 and no answer, the cards whole", widthDp = 412, heightDp = 110, locale = "ru")
@Composable
private fun DisabledKeyPreview() = OverPicture { BottomRow(Bookmark.Entry, practiceMs = null, keyEnabled = false) }

@Preview(name = "Bottom row · 360: cards of 124; 320: cards of 104, no icons", widthDp = 360, heightDp = 420, locale = "ru")
@Composable
private fun NarrowRowPreview() = OverPicture {
    BottomRow(Bookmark.Entry, practiceMs = null)
    BottomRow(Bookmark.Running(CONCERTO, minutesLeft = 12, progress = 0.4f), practiceMs = PRACTICE_MS)
    Box(Modifier.width(320.dp)) { BottomRow(Bookmark.Entry, practiceMs = null) }
    Box(Modifier.width(320.dp)) { BottomRow(Bookmark.Running(CONCERTO, minutesLeft = 12, progress = 0.4f), practiceMs = PRACTICE_MS) }
}

@Preview(name = "Bottom row · 360 at the font 1.5: the words step down, the first line wraps, never a word broken", widthDp = 360, heightDp = 240, fontScale = 1.5f, locale = "ru")
@Composable
private fun LargeFontRowPreview() = OverPicture {
    BottomRow(Bookmark.Entry, practiceMs = null)
    BottomRow(Bookmark.Running(CONCERTO, minutesLeft = 12, progress = 0.4f), practiceMs = PRACTICE_MS)
}

@Preview(name = "Bottom row · de on 320 at the font 1.5", widthDp = 320, heightDp = 240, fontScale = 1.5f, locale = "de")
@Composable
private fun GermanRowPreview() = OverPicture {
    BottomRow(Bookmark.Entry, practiceMs = null)
    BottomRow(Bookmark.Running(CONCERTO, minutesLeft = 12, progress = 0.4f), practiceMs = PRACTICE_MS)
}

@Preview(name = "Bottom row · fr on 320 at the font 1.5", widthDp = 320, heightDp = 240, fontScale = 1.5f, locale = "fr")
@Composable
private fun FrenchRowPreview() = OverPicture {
    BottomRow(Bookmark.Entry, practiceMs = null)
    BottomRow(Bookmark.Running(CONCERTO, minutesLeft = 12, progress = 0.4f), practiceMs = PRACTICE_MS)
}

@Preview(name = "Bottom row · pt on 360 at the font 1.3", widthDp = 360, heightDp = 240, fontScale = 1.3f, locale = "pt")
@Composable
private fun PortugueseRowPreview() = OverPicture {
    BottomRow(Bookmark.Entry, practiceMs = null)
    BottomRow(Bookmark.Running(CONCERTO, minutesLeft = 12, progress = 0.4f), practiceMs = PRACTICE_MS)
}

@Preview(name = "Bottom row · es on 360 at the font 1.3", widthDp = 360, heightDp = 240, fontScale = 1.3f, locale = "es")
@Composable
private fun SpanishRowPreview() = OverPicture {
    BottomRow(Bookmark.Entry, practiceMs = null)
    BottomRow(Bookmark.Running(CONCERTO, minutesLeft = 12, progress = 0.4f), practiceMs = PRACTICE_MS)
}

@Preview(name = "Bottom row · plain build: the glass on the card colour", widthDp = 412, heightDp = 110, locale = "ru")
@Composable
private fun PlainRowPreview() = OverPicture(plain = true) { BottomRow(Bookmark.Entry, practiceMs = null) }

private val Ribbon = RecordingRibbon(
    listOf(
        RecordingBar(30, Zone.IN_TUNE), RecordingBar(22, Zone.IN_TUNE), RecordingBar(12, Zone.NEAR), RecordingBar(26, Zone.IN_TUNE),
        RecordingBar(8, Zone.OFF), RecordingBar(18, Zone.IN_TUNE), RecordingBar(14, Zone.NEAR), RecordingBar(34, Zone.IN_TUNE),
    ),
    span = 200f,
)

@Preview(name = "Strip of a take · its glass of 50: the dot and its halo, «запись», the time, the ribbon without a track", widthDp = 412, heightDp = 220, locale = "ru")
@Composable
private fun StripPreview() = OverPicture {
    RecordingStrip(RecordingState(elapsedMs = 84_000), ribbon = { Ribbon }, modifier = Modifier.padding(horizontal = LiveDimens.RecordingStripSide))
    Box(Modifier.fillMaxWidth().height(70.dp)) {
        Picture(light = true, Modifier.matchParentSize())
        RecordingStrip(
            RecordingState(elapsedMs = 91_000),
            ribbon = { Ribbon },
            modifier = Modifier.align(Alignment.Center).padding(horizontal = LiveDimens.RecordingStripSide),
        )
    }
}

@Preview(name = "Strip of a take · de at the font 1.5 on 360", widthDp = 360, heightDp = 100, fontScale = 1.5f, locale = "de")
@Composable
private fun StripLargeFontPreview() = OverPicture {
    RecordingStrip(RecordingState(elapsedMs = 612_000), ribbon = { Ribbon }, modifier = Modifier.padding(horizontal = LiveDimens.RecordingStripSide))
}

/** The card «нет разрешения» [width] wide in a place [height] high, as Live gives it. */
@Composable
private fun Prompt(width: Dp, height: Dp) {
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        MicPermissionPrompt(onGrantClick = {}, modifier = Modifier.width(width))
    }
}

@Preview(name = "No permission · the card whole: the plate, the title, the text, «Разрешить доступ»", widthDp = 412, heightDp = 480, locale = "ru")
@Composable
private fun PromptPreview() = OverPicture { Prompt(360.dp, 400.dp) }

@Preview(name = "No permission · a lower place: the plate goes first, then the text scrolls in whole lines", widthDp = 360, heightDp = 560, locale = "ru")
@Composable
private fun PromptLowPreview() = OverPicture {
    Prompt(308.dp, 250.dp)
    // 360 × 640 in «Настройка»: not a line under the title of 22 — it steps down to 18 and goes on one line, a line of the text under it
    Prompt(308.dp, 182.dp)
}

@Preview(name = "No permission · 360 in «Настройка» at the font 1.5: no line of the text stands — it goes (heard with the title), the title as large as it may", widthDp = 360, heightDp = 260, fontScale = 1.5f, locale = "ru")
@Composable
private fun PromptLargeFontPreview() = OverPicture { Prompt(308.dp, 182.dp) }

@Preview(name = "No permission · 320 × 500 in «Настройка» at the font 1.5: the title at 16, the button 54", widthDp = 320, heightDp = 240, fontScale = 1.5f, locale = "ru")
@Composable
private fun PromptSmallPhonePreview() = OverPicture { Prompt(268.dp, 177.dp) }

@Preview(name = "No permission · a place lower than the least card: the title at 12, the air gives way, the button 54", widthDp = 320, heightDp = 200, fontScale = 1.3f, locale = "ru")
@Composable
private fun PromptLowestPreview() = OverPicture { Prompt(268.dp, 110.dp) }

@Preview(name = "No permission · the panel of 640 x 360 behind a cutout at the font 1.3 (fr)", widthDp = 270, heightDp = 336, fontScale = 1.3f, locale = "fr")
@Composable
private fun PromptPanelPreview() = OverPicture { Prompt(238.dp, 304.dp) }
