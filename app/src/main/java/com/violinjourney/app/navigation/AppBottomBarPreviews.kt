package com.violinjourney.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The tab bar of the redesign (spec 3.36.1, 5.29; components.html, screen 1): four tabs, the labels of one size — 12 sp
// where all four fit, down to 10 together for the French «Enregistrements» on 360 dp (11.5 on 412) — the mark of a running
// practice, the dimmed items of stage R6, and the compact landscape bar.

@Composable
private fun TallPreview(
    current: TopLevelDestination = TopLevelDestination.PRACTICE,
    practiceRunning: Boolean = false,
    dimmed: () -> Float = { 1f },
) {
    ViolinTheme { AppBottomBar(current = current, onSelect = {}, practiceRunning = practiceRunning, dimmed = dimmed) }
}

@Composable
private fun CompactPreview(current: TopLevelDestination = TopLevelDestination.PRACTICE, practiceRunning: Boolean = false) {
    ViolinTheme { AppBottomBar(current = current, onSelect = {}, practiceRunning = practiceRunning, compact = true) }
}

@Preview(name = "360 · ru", widthDp = 360, locale = "ru")
@Composable
private fun Tall360RuPreview() = TallPreview()

@Preview(name = "360 · en", widthDp = 360, locale = "en")
@Composable
private fun Tall360EnPreview() = TallPreview()

@Preview(name = "360 · fr, all four at 10 sp", widthDp = 360, locale = "fr")
@Composable
private fun Tall360FrPreview() = TallPreview(TopLevelDestination.HISTORY)

@Preview(name = "360 · de", widthDp = 360, locale = "de")
@Composable
private fun Tall360DePreview() = TallPreview()

@Preview(name = "360 · it", widthDp = 360, locale = "it")
@Composable
private fun Tall360ItPreview() = TallPreview()

@Preview(name = "412 · ru, «Репертуар» selected", widthDp = 412, locale = "ru")
@Composable
private fun Tall412RuPreview() = TallPreview(TopLevelDestination.REPERTOIRE)

@Preview(name = "412 · fr, 11.5 sp", widthDp = 412, locale = "fr")
@Composable
private fun Tall412FrPreview() = TallPreview(TopLevelDestination.HISTORY)

@Preview(name = "412 · a practice runs, seen from «Записи»", widthDp = 412, locale = "ru")
@Composable
private fun Tall412RunningPreview() = TallPreview(TopLevelDestination.HISTORY, practiceRunning = true)

@Preview(name = "412 · a practice runs, on «Занятия»", widthDp = 412, locale = "ru")
@Composable
private fun Tall412RunningSelectedPreview() = TallPreview(TopLevelDestination.PRACTICE, practiceRunning = true)

@Preview(name = "412 · dimmed with the light of Live (stage R6)", widthDp = 412, locale = "ru")
@Composable
private fun Tall412DimmedPreview() = TallPreview(TopLevelDestination.LIVE, practiceRunning = true, dimmed = { DIMMED })

@Preview(name = "640 · compact, fr", widthDp = 640, locale = "fr")
@Composable
private fun Compact640FrPreview() = CompactPreview(TopLevelDestination.REPERTOIRE)

@Preview(name = "892 · compact, ru, a practice runs", widthDp = 892, locale = "ru")
@Composable
private fun Compact892RuPreview() = CompactPreview(TopLevelDestination.HISTORY, practiceRunning = true)

/** The items while a note sounds on Live, as the instruments fade (spec 3.27, 5.29). */
private const val DIMMED = 0.38f
