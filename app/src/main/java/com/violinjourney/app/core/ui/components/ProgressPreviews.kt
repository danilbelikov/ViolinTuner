package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.components.StreakChip
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_weekdays
import org.jetbrains.compose.resources.stringArrayResource

// The ring of the level, the bars of a week and the chip of the streak of stage 102 (spec 3.36.1, 5.29; components.html, «Прогресс»).

private const val LEVEL = 5
private const val PROGRESS = 0.94f
private const val PHOTO_PX = 160

/** A stand-in for a profile photo, in the two tones of the mockup's placeholder — data of the picture, not a colour of the app. */
internal fun standInPhoto(): ImageBitmap {
    val bitmap = ImageBitmap(PHOTO_PX, PHOTO_PX)
    val side = PHOTO_PX.toFloat()
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(side, side)) {
        drawRect(
            Brush.linearGradient(
                listOf(Color(red = 107, green = 90, blue = 140), Color(red = 44, green = 38, blue = 64)),
                start = Offset(side * 0.3f, 0f),
                end = Offset(side * 0.7f, side),
            ),
        )
        // a head and shoulders, so a crop that goes wrong is seen
        drawCircle(Color(red = 233, green = 221, blue = 255).copy(alpha = 0.5f), radius = side * 0.18f, center = Offset(side / 2, side * 0.4f))
        drawCircle(Color(red = 233, green = 221, blue = 255).copy(alpha = 0.5f), radius = side * 0.34f, center = Offset(side / 2, side * 1.02f))
    }
    return bitmap
}

@Composable
private fun Ground(ground: @Composable () -> Color = { MaterialTheme.colorScheme.surface }, content: @Composable ColumnScope.() -> Unit) = ViolinTheme {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ground())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun Caption(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

/** The path row of R2 in miniature: the ring, the sum in the accent and the level under it. */
@Composable
private fun PathRow(photo: ImageBitmap?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LevelRing(level = LEVEL, progress = { PROGRESS }, photo = photo)
        Column(Modifier.padding(start = 12.dp)) {
            Text("47 ч 17 мин", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold))
            Text("Уровень 5 · Гаммы", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
        }
    }
}

@Preview(name = "LevelRing · 48 in the path row: without a photo and with one (the number in a badge)", widthDp = 412, heightDp = 200, locale = "ru")
@Composable
private fun LevelRingRowPreview() = Ground {
    val photo = remember { standInPhoto() }
    PathRow(photo = null)
    PathRow(photo = photo)
}

@Preview(name = "LevelRing · 72 in «Мой путь»: without a photo and with one, on the ground of the sheet", widthDp = 412, heightDp = 150, locale = "ru")
@Composable
private fun LevelRingSheetPreview() = Ground(ground = { MaterialTheme.colorScheme.surfaceContainer }) {
    val photo = remember { standInPhoto() }
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        LevelRing(LEVEL, progress = { PROGRESS }, size = LevelRingSize.Sheet, ground = MaterialTheme.colorScheme.surfaceContainer)
        LevelRing(LEVEL, progress = { PROGRESS }, size = LevelRingSize.Sheet, photo = photo, ground = MaterialTheme.colorScheme.surfaceContainer)
    }
}

@Preview(name = "LevelRing · the first level at 0 % and the last one full", widthDp = 412, heightDp = 150, locale = "ru")
@Composable
private fun LevelRingEndsPreview() = Ground {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        LevelRing(level = 1, progress = { 0f })
        LevelRing(level = 15, progress = { 1f })
        LevelRing(level = 1, progress = { 0f }, size = LevelRingSize.Sheet)
        LevelRing(level = 15, progress = { 1f }, size = LevelRingSize.Sheet)
    }
}

/** A week of the mockup: 5 h 45 min, Wednesday missed, today — Sunday — 45 min. */
private val WeekMinutes = listOf(35, 50, 0, 85, 40, 90, 45)

@Composable
private fun week(minutes: List<Int>, today: Int): List<WeekBarDay> {
    val labels = stringArrayResource(Res.array.practice_weekdays)
    return labels.mapIndexed { index, label ->
        val day = when {
            index < today -> WeekDayWhen.Past
            index == today -> WeekDayWhen.Today
            else -> WeekDayWhen.Future
        }
        WeekBarDay(label, minutes[index], day)
    }
}

private val scaleMinutes = PracticeConfig().fillLevelMinutes.last()

@Composable
private fun CardOfBars(days: List<WeekBarDay>, running: Boolean, modifier: Modifier = Modifier, size: WeekBarsSize = WeekBarsSize.Regular) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, AppShapes.M)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        WeekBars(days, running = running, scaleMinutes = scaleMinutes, description = "Неделя: 5 ч 45 мин; сегодня 45 мин", size = size)
    }
}

@Preview(name = "WeekBars · an ordinary week: Wednesday missed, today Sunday", widthDp = 412, heightDp = 140, locale = "ru")
@Composable
private fun WeekBarsPreview() = Ground { CardOfBars(week(WeekMinutes, today = 6), running = false) }

@Preview(name = "WeekBars · a practice runs: today hatched", widthDp = 412, heightDp = 140, locale = "ru")
@Composable
private fun WeekBarsRunningPreview() = Ground { CardOfBars(week(WeekMinutes, today = 6), running = true) }

@Preview(name = "WeekBars · the middle of a week: days to come are empty tracks", widthDp = 412, heightDp = 140, locale = "ru")
@Composable
private fun WeekBarsMidweekPreview() = Ground { CardOfBars(week(listOf(35, 50, 0, 20, 0, 0, 0), today = 3), running = false) }

@Preview(name = "WeekBars · landscape: 32 high in the left column of 280", widthDp = 320, heightDp = 120, locale = "ru")
@Composable
private fun WeekBarsSmallPreview() = Ground { CardOfBars(week(WeekMinutes, today = 6), running = false, size = WeekBarsSize.Small, modifier = Modifier.width(280.dp)) }

@Preview(name = "WeekBars · an empty week: missed days dashed, today still today", widthDp = 412, heightDp = 140, locale = "ru")
@Composable
private fun WeekBarsEmptyPreview() = Ground { CardOfBars(week(List(7) { 0 }, today = 2), running = false) }

@Preview(name = "StreakChip · 1, 2, 4, 8, 30 days; 0 — nothing", widthDp = 412, heightDp = 330, locale = "ru")
@Composable
private fun StreakChipPreview() = Ground(ground = { MaterialTheme.colorScheme.surfaceContainer }) {
    for (days in listOf(1, 2, 4, 8, 30, 0)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caption("$days:")
            StreakChip(days = days, running = false, scope = null, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Preview(name = "StreakChip · de", widthDp = 412, heightDp = 120, locale = "de")
@Composable
private fun StreakChipDePreview() = Ground(ground = { MaterialTheme.colorScheme.surfaceContainer }) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StreakChip(days = 1, running = false, scope = null)
        StreakChip(days = 8, running = false, scope = null)
        StreakChip(days = 30, running = false, scope = null)
    }
}

@Preview(name = "StreakChip · rolling after a sheet: the flame of 9 days, the words still at 7 and 8", widthDp = 412, heightDp = 120, locale = "ru")
@Composable
private fun StreakChipRollingPreview() = Ground(ground = { MaterialTheme.colorScheme.surfaceContainer }) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StreakChip(days = 9, running = false, scope = null, shownDays = 7)
        StreakChip(days = 9, running = false, scope = null, shownDays = 8)
        StreakChip(days = 9, running = false, scope = null)
    }
}
