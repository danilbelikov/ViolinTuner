package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_streak_days_description_few
import com.violinjourney.app.shared.resources.practice_streak_days_description_many
import com.violinjourney.app.shared.resources.practice_streak_days_description_one
import com.violinjourney.app.shared.resources.practice_streak_days_few
import com.violinjourney.app.shared.resources.practice_streak_days_many
import com.violinjourney.app.shared.resources.practice_streak_days_one
import org.jetbrains.compose.resources.stringResource

// The chip of the streak (spec 5.29; components.html, «Прогресс»).
private val ChipHeight = 28.dp
private val ChipEnd = 10.dp
private val ChipGap = 4.dp
private val Capsule = RoundedCornerShape(percent = 50)
private const val GROUND_ALPHA = 0.16f
private const val TABULAR_FIGURES = "tnum"

/**
 * The chip of the streak (spec 3.36.1, 5.29; 01-practice п. 7): «8 дней» in the warm colour of the flame on the same colour at
 * 16 %, a capsule of 28 — on «Занятия» only: in the head of «Сегодня» and of the card «Занятие идёт» (3.36.2). «Мой путь» has no
 * chip, and the recap has a streak card of its own (3.36.3, 5.29 R3: the number of 26 sp, the flame of 20 / 24 and «+1 день») —
 * not this chip. There is no streak of zero: without one the chip is not drawn at all — nobody is shamed for a missed day. The
 * flame stands before the number from three days on (5.12): 16 dp while the streak is young, 18 from seven days, with the bright
 * core from thirty; before that the chip is the word alone. The flame lives
 * as [StreakFlame] does — it sways and flares when the streak grows before the eyes, stands still while a practice [running],
 * and tells [onSway] so the living «Начать занятие» rests meanwhile; [scope] is what makes numbers other numbers, as there.
 *
 * TalkBack reads «8 дней подряд»; the flame says nothing (3.18). The number is not rolled here — that is R2's call.
 */
@Composable
fun StreakChip(days: Int, running: Boolean, scope: Any?, modifier: Modifier = Modifier, onSway: (Boolean) -> Unit = {}) {
    if (!StreakChipMath.shown(days)) return
    val flame = ViolinTheme.practiceColors.flameOuter
    val description = stringResource(
        Formats.plural(days, Res.string.practice_streak_days_description_one, Res.string.practice_streak_days_description_few, Res.string.practice_streak_days_description_many),
        days,
    )
    Row(
        modifier = modifier
            .heightIn(min = ChipHeight)
            .background(flame.copy(alpha = GROUND_ALPHA), Capsule)
            .padding(start = StreakChipMath.startPadding(days), end = ChipEnd)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        // below three days it draws nothing and takes no room, but stays to see the streak reach them
        StreakFlame(streakDays = days, running = running, scope = scope, maxSize = StreakChipMath.flameSize(days), onSway = onSway)
        Text(
            text = stringResource(Formats.plural(days, Res.string.practice_streak_days_one, Res.string.practice_streak_days_few, Res.string.practice_streak_days_many), days),
            color = flame,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/** The rules of the chip, pure, with a test: whether it is there, how big its flame is, how far its words stand from its edge. */
internal object StreakChipMath {
    private val FlameYoung = 16.dp
    private val FlameFull = 18.dp
    private val StartWithFlame = 7.dp
    private val StartWithout = 10.dp

    fun shown(days: Int): Boolean = days > 0

    fun flameSize(days: Int): Dp = if (days >= PracticeMotion.FLAME_FULL_FROM_DAYS) FlameFull else FlameYoung

    fun startPadding(days: Int): Dp = if (FlameMath.stageOf(days) == FlameMath.Stage.NONE) StartWithout else StartWithFlame
}
