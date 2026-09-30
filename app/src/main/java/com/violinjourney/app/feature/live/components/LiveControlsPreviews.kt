package com.violinjourney.app.feature.live.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.live.LiveMode
import com.violinjourney.app.feature.live.LiveReducer
import com.violinjourney.app.feature.live.ScaleMath
import com.violinjourney.app.feature.live.ScaleSpec
import com.violinjourney.app.feature.live.StatusDot
import com.violinjourney.app.feature.live.StatusLine
import com.violinjourney.app.feature.live.StatusMessage
import com.violinjourney.app.feature.live.TuningState
import kotlin.math.abs

// The controls of the top of Live and of «Настройка» one by one (spec 3.36.6, 5.29 R6): the switcher, the plate of the status line,
// the strings, the scale — over a picture, a dark room or a light hall, so the glass and its edge are seen.

/** A picture under the controls: the room at dusk or the cream of a light hall — data of the picture, not colours of the app. */
@Composable
internal fun Picture(light: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val colors = if (light) {
            listOf(Color(red = 241, green = 232, blue = 212), Color(red = 226, green = 204, blue = 168), Color(red = 250, green = 246, blue = 236))
        } else {
            listOf(Color(red = 58, green = 40, blue = 30), Color(red = 110, green = 72, blue = 44), Color(red = 24, green = 18, blue = 16))
        }
        drawRect(Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height)))
    }
}

/** The controls over [light] or dark picture; [plain] — the Live of `-PplainLive=true`, no picture and the card colour under them. */
@Composable
internal fun OverPicture(light: Boolean = false, plain: Boolean = false, content: @Composable () -> Unit) = ViolinTheme {
    CompositionLocalProvider(LocalLivePlain provides plain) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (!plain) Picture(light, Modifier.fillMaxSize())
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

private val TUNING = LiveReducer.stringHzOf(IntonationConfig())

private fun tuning(locked: ViolinString? = null, target: ViolinString? = locked) = TuningState(lockedString = locked, targetString = target, stringHz = TUNING)

/** The top row as Live lays it upright: the switcher in the middle, the gear 16 from the right edge. */
@Composable
private fun TopRowPreview(mode: LiveMode, dimmed: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val plan = rememberSwitcherPlan(centered = maxWidth - LiveDimens.GearTouch * 2, beside = maxWidth - LiveDimens.GearTouch)
        Box(Modifier.fillMaxWidth()) {
            ModeSwitcher(
                mode = mode,
                onSelect = {},
                plan = plan,
                enabled = !dimmed,
                modifier = Modifier
                    .align(if (plan.centered) Alignment.Center else Alignment.CenterStart)
                    .graphicsLayer { alpha = if (dimmed) LiveDimens.DISABLED_ALPHA else 1f },
            )
            SettingsGear(onClick = {}, enabled = !dimmed, modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer { alpha = if (dimmed) LiveDimens.DISABLED_ALPHA else 1f })
        }
    }
}

@Preview(name = "Switcher · «Игра» and «Настройка», the gear; over the room and over a light hall", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun SwitcherPreview() = OverPicture {
    TopRowPreview(LiveMode.PLAY)
    TopRowPreview(LiveMode.TUNING)
    Box(Modifier.fillMaxWidth()) {
        Picture(light = true, Modifier.matchParentSize())
        TopRowPreview(LiveMode.TUNING)
    }
}

@Preview(name = "Switcher · while a take records: 0.4 and no answer", widthDp = 412, heightDp = 96, locale = "ru")
@Composable
private fun SwitcherRecordingPreview() = OverPicture { TopRowPreview(LiveMode.PLAY, dimmed = true) }

@Preview(name = "Switcher · 320 at the font 1.5: 12 sp, moved up to the gear, not under it", widthDp = 320, heightDp = 160, fontScale = 1.5f, locale = "ru")
@Composable
private fun SwitcherNarrowLargeFontPreview() = OverPicture {
    TopRowPreview(LiveMode.PLAY)
    TopRowPreview(LiveMode.TUNING)
}

@Preview(name = "Switcher · de on 320 at the font 1.3", widthDp = 320, heightDp = 96, fontScale = 1.3f, locale = "de")
@Composable
private fun SwitcherGermanPreview() = OverPicture { TopRowPreview(LiveMode.TUNING) }

@Preview(name = "Switcher · fr on 320 at the font 1.3", widthDp = 320, heightDp = 96, fontScale = 1.3f, locale = "fr")
@Composable
private fun SwitcherFrenchPreview() = OverPicture { TopRowPreview(LiveMode.TUNING) }

@Preview(name = "Switcher · plain build: the card colour instead of the glass", widthDp = 412, heightDp = 96, locale = "ru")
@Composable
private fun SwitcherPlainPreview() = OverPicture(plain = true) { TopRowPreview(LiveMode.TUNING) }

@Preview(name = "Plate · may play, may not, locked D; over the room and a light hall", widthDp = 412, heightDp = 300, locale = "ru")
@Composable
private fun PlatePreview() = OverPicture {
    StatusLineRow(StatusLine(StatusDot.READY, StatusMessage.PLAY), tuning(), plate = true)
    StatusLineRow(StatusLine(StatusDot.BLOCKED, StatusMessage.TOO_NOISY), tuning(), plate = true)
    StatusLineRow(StatusLine(StatusDot.BLOCKED, StatusMessage.MIC_UNAVAILABLE), tuning(), plate = true)
    StatusLineRow(StatusLine(StatusDot.READY, StatusMessage.TUNE_AUTO), tuning(), plate = true)
    StatusLineRow(StatusLine(StatusDot.READY, StatusMessage.TUNE_LOCKED), tuning(ViolinString.D4), plate = true)
}

@Preview(name = "Plate · over a light hall", widthDp = 412, heightDp = 120, locale = "ru")
@Composable
private fun PlateLightPreview() = OverPicture(light = true) {
    StatusLineRow(StatusLine(StatusDot.READY, StatusMessage.TUNE_LOCKED), tuning(ViolinString.D4), plate = true)
    StatusLineRow(StatusLine(StatusDot.BLOCKED, StatusMessage.TOO_NOISY), tuning(), plate = true)
}

@Preview(name = "Plate · plain build: no plate", widthDp = 412, heightDp = 80, locale = "ru")
@Composable
private fun PlatePlainPreview() = OverPicture(plain = true) {
    StatusLineRow(StatusLine(StatusDot.READY, StatusMessage.PLAY), tuning(), plate = false)
}

private val Lit: () -> Float = { 1f }
private val Dark: () -> Float = { 0.38f }

@Preview(name = "Strings · auto without a target; the nearest A sounds; D locked in silence", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun StringsPreview() = OverPicture {
    StringRow(tuning(), onStringClick = {}, light = Lit, base = 1f, modifier = Modifier.padding(horizontal = LiveDimens.StringRowSide))
    StringRow(tuning(target = ViolinString.A4), onStringClick = {}, light = Lit, base = 1f, modifier = Modifier.padding(horizontal = LiveDimens.StringRowSide))
    StringRow(tuning(ViolinString.D4), onStringClick = {}, light = Lit, base = 1f, modifier = Modifier.padding(horizontal = LiveDimens.StringRowSide))
}

@Preview(name = "Strings · the light out: the others 0.38, the target whole; without the permission all 0.4", widthDp = 412, heightDp = 200, locale = "ru")
@Composable
private fun StringsDimmedPreview() = OverPicture {
    StringRow(tuning(target = ViolinString.A4), onStringClick = {}, light = Dark, base = 1f, modifier = Modifier.padding(horizontal = LiveDimens.StringRowSide))
    StringRow(tuning(ViolinString.D4), onStringClick = {}, light = Dark, base = 1f, modifier = Modifier.padding(horizontal = LiveDimens.StringRowSide))
    StringRow(tuning(ViolinString.D4), onStringClick = {}, light = Lit, base = LiveDimens.CHROME_ALPHA_NO_MIC, modifier = Modifier.padding(horizontal = LiveDimens.StringRowSide))
}

@Preview(name = "Strings · over a light hall and in the plain build", widthDp = 412, heightDp = 200, locale = "ru")
@Composable
private fun StringsLightPreview() = OverPicture(light = true) {
    StringRow(tuning(target = ViolinString.A4), onStringClick = {}, light = Lit, base = 1f, modifier = Modifier.padding(horizontal = LiveDimens.StringRowSide))
    StringRow(tuning(ViolinString.D4), onStringClick = {}, light = Lit, base = 1f, modifier = Modifier.padding(horizontal = LiveDimens.StringRowSide))
}

@Preview(name = "Strings · 320 at the font 1.5: the letter and the hertz in 58", widthDp = 320, heightDp = 120, fontScale = 1.5f, locale = "ru")
@Composable
private fun StringsLargeFontPreview() = OverPicture {
    StringRow(tuning(ViolinString.E5), onStringClick = {}, light = Lit, base = 1f, modifier = Modifier.padding(horizontal = LiveDimens.StringRowSide))
}

@Preview(name = "Strings · plain build", widthDp = 412, heightDp = 110, locale = "ru")
@Composable
private fun StringsPlainPreview() = OverPicture(plain = true) {
    StringRow(tuning(target = ViolinString.A4), onStringClick = {}, light = Lit, base = 1f, modifier = Modifier.padding(horizontal = LiveDimens.StringRowSide))
}

@Composable
private fun ScalePreviewRow(cents: Double?, alpha: Float = 1f, height: Dp = LiveDimens.ScaleHeight) {
    val scale = ScaleSpec(IntonationConfig())
    val zones = ViolinTheme.zoneColors
    val halo = if (cents != null && abs(cents) <= scale.toleranceCents) zones.inTune else zones.near
    CentsScale(
        markerVisible = cents != null,
        markerFraction = { cents?.let { ScaleMath.markerFraction(it, scale).toFloat() } },
        inTuneFraction = ScaleMath.inTuneFraction(scale).toFloat(),
        haloColor = { halo },
        modifier = Modifier.padding(horizontal = LiveDimens.ScreenPadding).graphicsLayer { this.alpha = alpha },
        height = height,
    )
}

@Preview(name = "Scale · silence (0.45, no slider), in tune, +14, no permission (0.3), 28 of a low window", widthDp = 412, heightDp = 280, locale = "ru")
@Composable
private fun ScalePreview() = OverPicture {
    ScalePreviewRow(cents = null, alpha = LiveDimens.SCALE_ALPHA_IDLE)
    ScalePreviewRow(cents = 3.0)
    ScalePreviewRow(cents = 14.0)
    ScalePreviewRow(cents = null, alpha = LiveDimens.SCALE_ALPHA_NO_MIC)
    ScalePreviewRow(cents = 3.0, height = 28.dp)
}

