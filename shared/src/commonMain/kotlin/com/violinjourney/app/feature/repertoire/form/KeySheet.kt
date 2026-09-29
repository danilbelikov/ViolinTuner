package com.violinjourney.app.feature.repertoire.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.KeyMode
import com.violinjourney.app.core.domain.repertoire.MusicalKey
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.ReasonLine
import com.violinjourney.app.core.ui.components.SegmentedSwitch
import com.violinjourney.app.core.ui.components.appButtonsSharedSize
import com.violinjourney.app.feature.repertoire.components.TonicGrid
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.key_none
import com.violinjourney.app.shared.resources.key_pick_tonic_first
import com.violinjourney.app.shared.resources.piece_field_key
import com.violinjourney.app.shared.resources.piece_key_major
import com.violinjourney.app.shared.resources.piece_key_minor
import com.violinjourney.app.shared.resources.profile_done
import org.jetbrains.compose.resources.stringResource

// The sheet «Тональность» (spec 3.36.4, 5.29 R4; repertoire.html 4, «Лист тональности»).
private val TitleBottom = 8.dp
private val BetweenRows = 10.dp
private val ReasonTop = 6.dp
private val ButtonsTop = 16.dp
private val ButtonsGap = 10.dp

/** The buttons one under the other, where their halves would break a word even at [BUTTONS_MIN_SP]: 6 apart, as those of R1. */
private val StackedGap = 6.dp

/** The least size of the words of the two buttons (13 — the least of the names of the tiles, 5.29 R4): below it they stack. */
private const val BUTTONS_MIN_SP = 13f
private const val TITLE_SIZE = 20
private const val RESULT_SIZE = 22

/** The sign of 26 (5.29 R4): the three glyphs read at a glance. */
private const val SIGN_SIZE = 26
private val Signs = listOf("♭", "♮", "♯")

/**
 * The content of the sheet «Тональность» (spec 3.36.4): its title and, on the right, large, the [key] it adds up to in the German way
 * — as the list will show it; the tonics C … B (in two rows where seven of 48 do not stand in one); the sign ♭ · ♮ · ♯
 * and the mode мажор | минор, both at 0.38 and deaf until a tonic is picked, with «Сначала выберите тонику» under them. Lying (a
 * window wider than high) the sign and the mode stand in one row, halves 10 apart, so that the reason under them is in sight in 892 ×
 * 412 without scrolling (5.29 R4). Every choice goes into the draft at once; a tap on the picked tonic takes the key away (3.15).
 * The buttons are [KeySheetButtons]. Also the preview of the sheet, which a preview cannot open.
 */
@Composable
fun ColumnScope.KeySheetContent(key: MusicalKey?, onIntent: (PieceFormIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(bottom = TitleBottom)) {
        Text(
            text = stringResource(Res.string.piece_field_key),
            modifier = Modifier.weight(1f).alignByBaseline().semantics { heading() },
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = TITLE_SIZE.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold),
        )
        if (key != null) {
            Text(
                text = key.germanName,
                modifier = Modifier.alignByBaseline(),
                color = colors.primary,
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = RESULT_SIZE.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold),
            )
        }
    }
    TonicGrid(selected = key?.tonic, onClick = { onIntent(PieceFormIntent.TonicClicked(it)) }, inSheet = true)
    Spacer(Modifier.height(BetweenRows))
    // without a tonic there is nothing for a sign or a mode to belong to: they sleep, and the line under them says why
    val picked = key != null
    val window = LocalWindowInfo.current.containerSize
    if (window.width > window.height) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BetweenRows)) {
            SignSwitch(key, picked, onIntent, Modifier.weight(1f))
            ModeSwitch(key, picked, onIntent, Modifier.weight(1f))
        }
    } else {
        SignSwitch(key, picked, onIntent)
        Spacer(Modifier.height(BetweenRows))
        ModeSwitch(key, picked, onIntent)
    }
    if (!picked) ReasonLine(stringResource(Res.string.key_pick_tonic_first), Modifier.padding(top = ReasonTop))
}

/** ♭ · ♮ · ♯ of 26 on the ground of the screen. */
@Composable
private fun SignSwitch(key: MusicalKey?, picked: Boolean, onIntent: (PieceFormIntent) -> Unit, modifier: Modifier = Modifier) {
    val accidentals = Accidental.entries
    SegmentedSwitch(
        labels = Signs,
        selectedIndex = key?.let { accidentals.indexOf(it.accidental) },
        onSelect = { onIntent(PieceFormIntent.AccidentalSelected(accidentals[it])) },
        modifier = modifier,
        fontSize = SIGN_SIZE,
        containerColor = MaterialTheme.colorScheme.surface,
        enabled = picked,
    )
}

/** мажор | минор on the ground of the screen. */
@Composable
private fun ModeSwitch(key: MusicalKey?, picked: Boolean, onIntent: (PieceFormIntent) -> Unit, modifier: Modifier = Modifier) {
    val modes = KeyMode.entries
    SegmentedSwitch(
        labels = listOf(stringResource(Res.string.piece_key_major), stringResource(Res.string.piece_key_minor)),
        selectedIndex = key?.let { modes.indexOf(it.mode) },
        onSelect = { onIntent(PieceFormIntent.ModeSelected(modes[it])) },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        enabled = picked,
    )
}

/**
 * The buttons of «Тональность» in one row, halves 10 apart and of one height: «Без тональности» (soft) takes the key away and closes
 * the sheet; «Готово» (main, 48 — so that the tonics, the sign and the mode with the buttons stand in a landscape of 412 without
 * scrolling, 5.29 R4) only closes it, as a swipe and «назад» do: every choice is in the draft already. No word breaks inside
 * (3.36.4): where the widest word of a half does not fit it at 15 sp — «тональности» on a phone of 360 at the font 1.3 — both step
 * down together to 13 ([appButtonsSharedSize]); where not even that holds them, they stand one under the other as wide as the sheet,
 * «Готово» first, as the main button of every sheet (R1).
 */
@Composable
fun ColumnScope.KeySheetButtons(onIntent: (PieceFormIntent) -> Unit) {
    val none = stringResource(Res.string.key_none)
    val done = stringResource(Res.string.profile_done)
    val clear = { onIntent(PieceFormIntent.KeyCleared) }
    val close = { onIntent(PieceFormIntent.SheetHidden) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = ButtonsTop)) {
        val half = (maxWidth - ButtonsGap) / 2
        val size = appButtonsSharedSize(listOf(none to AppButtonStyle.Soft, done to AppButtonStyle.Main), half, compact = true, minSp = BUTTONS_MIN_SP)
        if (size != null) {
            // one height for both: a half whose words take two lines does not stand taller than its neighbour
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ButtonsGap)) {
                AppButton(none, clear, Modifier.weight(1f).fillMaxHeight(), style = AppButtonStyle.Soft, fontSize = size)
                AppButton(done, close, Modifier.weight(1f).fillMaxHeight(), compact = true, fontSize = size)
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StackedGap)) {
                AppButton(done, close, Modifier.fillMaxWidth(), compact = true)
                AppButton(none, clear, Modifier.fillMaxWidth(), style = AppButtonStyle.Soft)
            }
        }
    }
}
