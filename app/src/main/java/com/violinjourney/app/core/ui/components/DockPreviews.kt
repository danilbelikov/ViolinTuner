package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.components.StartPracticeButton
import com.violinjourney.app.navigation.AppBottomBar
import com.violinjourney.app.navigation.TopLevelDestination
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.capture_no_permission
import com.violinjourney.app.shared.resources.home_arrange
import com.violinjourney.app.shared.resources.home_shop
import com.violinjourney.app.shared.resources.home_travel
import com.violinjourney.app.shared.resources.home_travel_enough
import com.violinjourney.app.shared.resources.piece_field_notes
import com.violinjourney.app.shared.resources.piece_field_title
import com.violinjourney.app.shared.resources.piece_title_error
import com.violinjourney.app.shared.resources.practice_save
import com.violinjourney.app.shared.resources.practice_start
import com.violinjourney.app.shared.resources.take_no_permission
import com.violinjourney.app.shared.resources.take_record
import org.jetbrains.compose.resources.stringResource

// The bottom zone of stage 102 (spec 3.36.1, 5.29; components.html, «Закреплённая нижняя зона»). The metrics are given each time:
// the window of a preview is not the size of the preview, and the zone would take its fields from the wrong height. The zone is
// drawn over the tab bar (zIndex), as the roots of R2 are to draw it, so the glow of the living button is seen where it lies.

/** What scrolls under the zone: rows padded at the end by the height of the zone, as every list under one is. */
@Composable
private fun ListUnder(count: Int = 12) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = LocalDockInset.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count) { index ->
            ListGroup { ListRow("Этюд № ${index + 1}", onClick = {}, icon = AppIcons.NoteOne, caption = "Кайзер · 10 мин") }
        }
    }
}

@Composable
private fun OverTabs(tab: TopLevelDestination = TopLevelDestination.PRACTICE, compact: Boolean = false, content: @Composable (Modifier) -> Unit) = ViolinTheme {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        content(Modifier.weight(1f).fillMaxWidth().zIndex(1f))
        AppBottomBar(current = tab, onSelect = {}, compact = compact)
    }
}

@Preview(name = "Dock · 412 · the main button over the tabs, the list fades under it", widthDp = 412, heightDp = 420, locale = "ru")
@Composable
private fun DockMainPreview() = OverTabs { modifier ->
    AppDock(
        dock = { AppButton(stringResource(Res.string.practice_start), onClick = {}, Modifier.fillMaxWidth(), icon = AppIcons.Timer, compact = compact) },
        modifier = modifier,
        metrics = DockMetrics.Regular,
    ) { ListUnder() }
}

@Preview(name = "Dock · the living «Начать занятие»: the glow is not cut by the zone", widthDp = 412, heightDp = 420, locale = "ru")
@Composable
private fun DockLivingPreview() = OverTabs { modifier ->
    AppDock(
        dock = { StartPracticeButton(onClick = {}, calm = { true }, modifier = Modifier.fillMaxWidth().height(buttonHeight)) },
        modifier = modifier,
        metrics = DockMetrics.Regular,
    ) { ListUnder() }
}

@Preview(name = "Dock · the row of the home: the main with a caption, two halves of 48", widthDp = 412, heightDp = 480, locale = "ru")
@Composable
private fun DockHomeRowPreview() = ViolinTheme {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AppDock(
            dock = {
                AppButton(
                    stringResource(Res.string.home_travel),
                    onClick = {},
                    Modifier.fillMaxWidth(),
                    icon = AppIcons.Travel,
                    caption = stringResource(Res.string.home_travel_enough, "Вена", "Праги"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DockDefaults.RowGap)) {
                    AppButton(stringResource(Res.string.home_shop), onClick = {}, Modifier.weight(1f), style = AppButtonStyle.Outline, icon = AppIcons.Shop, compact = true)
                    AppButton(stringResource(Res.string.home_arrange), onClick = {}, Modifier.weight(1f), style = AppButtonStyle.Outline, icon = AppIcons.Arrange, compact = true)
                }
            },
            metrics = DockMetrics.Regular,
        ) { ListUnder() }
    }
}

@Preview(name = "Dock · no tabs: a dimmed «Сохранить» with its reason", widthDp = 412, heightDp = 480, locale = "ru")
@Composable
private fun DockReasonPreview() = ViolinTheme {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AppDock(
            dock = {
                AppButton(
                    stringResource(Res.string.practice_save),
                    onClick = {},
                    Modifier.fillMaxWidth(),
                    enabled = false,
                    reason = stringResource(Res.string.piece_title_error),
                )
            },
            metrics = DockMetrics.Regular,
            aboveKeyboard = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppField(value = TextFieldValue(""), onValueChange = {}, label = stringResource(Res.string.piece_field_title))
                AppField(
                    value = TextFieldValue(""),
                    onValueChange = {},
                    label = stringResource(Res.string.piece_field_notes),
                    singleLine = false,
                    minLines = 4,
                    keyboardOptions = KeyboardOptions.Default,
                )
                Spacer(Modifier.height(LocalDockInset.current))
            }
        }
    }
}

@Preview(name = "Dock · 360 × 640: the fields 8 / 16 / 10, the button stays 56", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun DockSmallPreview() = OverTabs { modifier ->
    AppDock(
        dock = { StartPracticeButton(onClick = {}, calm = { true }, modifier = Modifier.fillMaxWidth().height(buttonHeight)) },
        modifier = modifier,
        metrics = DockMetrics.Low,
    ) { ListUnder() }
}

@Composable
private fun LandscapeColumns(metrics: DockMetrics) = OverTabs(compact = true) { modifier ->
    Row(modifier) {
        // the zone belongs to the left column: as wide as it, the fade only over it; the column gives the sides
        AppDock(
            dock = { StartPracticeButton(onClick = {}, calm = { true }, modifier = Modifier.fillMaxWidth().height(buttonHeight)) },
            modifier = Modifier.width(280.dp).fillMaxHeight().padding(horizontal = 16.dp),
            fade = DockDefaults.FadeLeftColumn,
            padSides = false,
            metrics = metrics,
        ) { ListUnder(count = 6) }
        Box(Modifier.weight(1f).fillMaxHeight()) { ListUnder() }
    }
}

@Preview(name = "Dock · landscape 892 × 412: the left column of 280, the fade of 16, the sides from the column", widthDp = 892, heightDp = 412, locale = "ru")
@Composable
private fun DockLandscapePreview() = LandscapeColumns(DockMetrics.Low)

@Preview(name = "Dock · a window no higher than 360: the button of 48, the zone of 64", widthDp = 640, heightDp = 360, locale = "ru")
@Composable
private fun DockTinyPreview() = LandscapeColumns(DockMetrics.Tiny)

@Preview(name = "Dock · no permission: the line over the sleeping «Записать дубль»", widthDp = 412, heightDp = 420, locale = "ru")
@Composable
private fun DockPermissionPreview() = ViolinTheme {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AppDock(
            dock = {
                PermissionLine(reason = stringResource(Res.string.take_no_permission), onGrant = {}, modifier = Modifier.fillMaxWidth())
                AppButton(stringResource(Res.string.take_record), onClick = {}, Modifier.fillMaxWidth(), icon = AppIcons.Mic, enabled = false)
            },
            metrics = DockMetrics.Regular,
        ) { ListUnder() }
    }
}

@Preview(name = "Dock · no permission over the preview of the own camera: dense glass .82", widthDp = 412, heightDp = 560, locale = "ru")
@Composable
private fun DockPermissionCameraPreview() = ViolinTheme {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BusyPicture(Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            PermissionLine(
                reason = stringResource(Res.string.capture_no_permission),
                onGrant = {},
                modifier = Modifier.fillMaxWidth(),
                icon = AppIcons.Camera,
                overPicture = true,
            )
            Spacer(Modifier.height(DockDefaults.RowGap))
            AppButton(stringResource(Res.string.take_record), onClick = {}, Modifier.fillMaxWidth(), icon = AppIcons.Video, enabled = false)
        }
    }
}

/** A busy picture — light, dark and colour side by side — as the preview of a camera is; data of the picture, not colours of the app. */
@Composable
internal fun BusyPicture(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            Brush.linearGradient(
                listOf(Color(red = 241, green = 238, blue = 230), Color(red = 142, green = 47, blue = 63), Color(red = 30, green = 60, blue = 110), Color.White),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        val stripe = size.width / 9
        for (i in 0 until 9 step 2) {
            drawRect(Color.White.copy(alpha = 0.55f), topLeft = Offset(i * stripe, size.height * 0.55f), size = Size(stripe * 0.6f, size.height * 0.45f))
        }
        drawCircle(Color(red = 232, green = 214, blue = 184), radius = size.minDimension * 0.22f, center = Offset(size.width * 0.3f, size.height * 0.35f))
    }
}
