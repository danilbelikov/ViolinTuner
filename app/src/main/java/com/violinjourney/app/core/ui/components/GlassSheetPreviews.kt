package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.mode_tuning
import com.violinjourney.app.shared.resources.practice_discard
import com.violinjourney.app.shared.resources.practice_save
import com.violinjourney.app.shared.resources.practice_start
import com.violinjourney.app.shared.resources.practice_stop
import com.violinjourney.app.shared.resources.tuning_string_hz
import com.violinjourney.app.shared.resources.venue_home
import org.jetbrains.compose.resources.stringResource

// The glass and the frame of a sheet of stage 102 (spec 3.36.1, 5.29; components.html, «Стекло», «Лист»). A window does not draw
// in a preview: the sheet is its card, over a screen under the scrim of a sheet.

/** A picture under the glass — a dark room or a light hall; data of the picture, not colours of the app. */
@Composable
private fun Picture(light: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val colors = if (light) {
            listOf(Color(red = 241, green = 238, blue = 230), Color(red = 232, green = 214, blue = 184), Color.White)
        } else {
            listOf(Color(red = 58, green = 40, blue = 30), Color(red = 110, green = 72, blue = 44), Color(red = 24, green = 18, blue = 16))
        }
        drawRect(Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height)))
    }
}

@Composable
private fun GlassScene(light: Boolean, place: String, busy: Boolean = false) = ViolinTheme {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(20.dp)) {
        Box(Modifier.fillMaxSize().clip(AppShapes.M)) {
            if (busy) BusyPicture(Modifier.fillMaxSize()) else Picture(light, Modifier.fillMaxSize())
            GlassTag(place, Modifier.align(Alignment.TopStart).padding(12.dp), icon = AppIcons.House, strong = busy)
            GlassPlate(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).height(60.dp),
                shape = AppShapes.M,
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                AppIcon(AppIcons.Timer, contentDescription = null, size = IconSizes.InFilledButton)
                Text(stringResource(Res.string.practice_start), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Preview(name = "Glass · a dark picture: the tag and the card of 60 at .72", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun GlassDarkPreview() = GlassScene(light = false, place = stringResource(Res.string.venue_home))

@Preview(name = "Glass · a light picture: the words still read at .72", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun GlassLightPreview() = GlassScene(light = true, place = "Вена · Золотой зал")

@Preview(name = "Glass · a busy picture: the city tag at .82", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun GlassBusyPreview() = GlassScene(light = true, place = "Прага", busy = true)

/** A control of Live on the glass (spec 5.29 R6): the inner edge of 1 dp, white at 12 %; [ground] where there is no picture. */
@Composable
private fun EdgePlate(ground: Color? = null) {
    GlassPlate(Modifier.height(44.dp), edge = true, ground = ground, contentPadding = PaddingValues(horizontal = 18.dp)) {
        Text(stringResource(Res.string.mode_tuning), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        Text(stringResource(Res.string.tuning_string_hz, 294), color = ViolinTheme.glassCaption, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp))
    }
}

@Preview(name = "Glass · the controls of Live: the edge over a dark and a light picture, the card colour without one", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun GlassEdgePreview() = ViolinTheme {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(false, true).forEach { light ->
            Box(Modifier.fillMaxWidth().height(64.dp).clip(AppShapes.M), contentAlignment = Alignment.Center) {
                Picture(light, Modifier.fillMaxSize())
                EdgePlate()
            }
        }
        // the plain Live: the glass would melt into the dark field
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            EdgePlate(ground = MaterialTheme.colorScheme.surfaceContainer)
        }
    }
}

@Composable
private fun EndPracticeSheet(modifier: Modifier = Modifier) {
    AppSheetCard(modifier) {
        SectionLabel(stringResource(Res.string.practice_stop))
        Spacer(Modifier.height(12.dp))
        Text(
            text = "47 мин",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
        )
        Text("17:55 — 18:42", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
        AppSheetButtons(
            main = stringResource(Res.string.practice_save),
            onMain = {},
            quiet = stringResource(Res.string.practice_discard),
        )
    }
}

@Composable
private fun UnderSheet(content: @Composable () -> Unit) = ViolinTheme {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            repeat(4) { ListGroup { ListRow("Этюд № ${it + 1}", onClick = {}, icon = AppIcons.NoteOne) }; Spacer(Modifier.height(8.dp)) }
        }
        Box(Modifier.fillMaxSize().background(ViolinTheme.sheetScrim))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { content() }
    }
}

@Preview(name = "Sheet · portrait 412: the scrim .55, the handle, the main button and the quiet one", widthDp = 412, heightDp = 560, locale = "ru")
@Composable
private fun SheetPreview() = UnderSheet { EndPracticeSheet() }

@Preview(name = "Sheet · landscape 892: no wider than 640, in the middle", widthDp = 892, heightDp = 412, locale = "ru")
@Composable
private fun SheetLandscapePreview() = UnderSheet { EndPracticeSheet() }
