package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.scale.Scale
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.SegmentedSwitch
import com.violinjourney.app.core.ui.components.dimmedWhen
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.repertoire.PieceMeta
import com.violinjourney.app.feature.repertoire.components.MetaLine
import com.violinjourney.app.feature.repertoire.components.statusLabel
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.piece_edit
import com.violinjourney.app.shared.resources.piece_field_status
import com.violinjourney.app.shared.resources.session_back
import org.jetbrains.compose.resources.stringResource

// The head of an element (spec 3.36.4, 5.29 R4, «Произведение»; repertoire.html 3, landscape.html 2).
private val TopBarButton = 48.dp
private const val TITLE_SIZE = 26
private const val TITLE_LINE_HEIGHT = 1.15f
private val MetaTop = 6.dp
private val StatusTop = 14.dp

/**
 * The bar of an element: «назад», the name — on the bar only once the large one has scrolled away upright ([titleVisible]), and
 * always lying — and «Изменить» with its pencil on the right, a text button of 48; asleep while a take runs ([editable] false): the
 * form would end it (spec 3.15). 56 upright, 48 lying.
 */
@Composable
internal fun PieceTopBar(title: String, titleVisible: Boolean, height: Dp, onIntent: (PieceIntent) -> Unit, editable: Boolean = true, loaded: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val back = stringResource(Res.string.session_back)
        Box(
            modifier = Modifier
                .size(TopBarButton)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onIntent(PieceIntent.BackClicked) }
                .semantics { contentDescription = back },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Back, contentDescription = null, tint = colors.onSurface)
        }
        // The large title below says it first upright; this one takes over once that has scrolled away.
        Box(modifier = Modifier.weight(1f)) {
            // not the one of RowScope: it would grow the row sideways
            androidx.compose.animation.AnimatedVisibility(visible = titleVisible, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold),
                )
            }
        }
        if (loaded) {
            AppButton(
                text = stringResource(Res.string.piece_edit),
                onClick = { onIntent(PieceIntent.EditClicked) },
                modifier = Modifier.dimmedWhen(!editable),
                style = AppButtonStyle.Text,
                icon = AppIcons.Pencil,
            )
        }
    }
}

/**
 * The name of an element large (26 sp / 800, up to two lines), the line of what is set — «И. С. Бах · G-dur · [метроном] 100»,
 * of a scale its kind and tempo — and the status as a switch of three steps (spec 3.36.4).
 */
@Composable
internal fun HeaderBlock(header: PieceHeader, scale: Scale?, onIntent: (PieceIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = header.title,
            modifier = Modifier.semantics { heading() },
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = TITLE_SIZE.sp, lineHeight = (TITLE_SIZE * TITLE_LINE_HEIGHT).sp, fontWeight = FontWeight.ExtraBold),
        )
        PieceMetaLine(header, scale, Modifier.padding(top = MetaTop))
        StatusSwitch(header.status, onIntent, Modifier.padding(top = StatusTop))
    }
}

/** «И. С. Бах · G-dur · [метроном] 100» — only what is set; of a scale its kind and its tempo; nothing at all — no line. */
@Composable
internal fun PieceMetaLine(header: PieceHeader, scale: Scale?, modifier: Modifier = Modifier) {
    val parts = PieceMeta.parts(header.composer, header.keyName, header.tempoBpm, scaleKind = scale?.spec?.kind)
    MetaLine(parts, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp), modifier = modifier)
}

/**
 * The status as a switch of three steps (spec 3.36.4): every step in sight, one tap changes it at once; «В репертуаре» of a piece,
 * «Выучено» of a scale, an étude, a stroke ([com.violinjourney.app.feature.repertoire.components.LocalExerciseWords]). The words are
 * never cut — a step grows to two lines at its space, and no word breaks: equal steps while each holds its widest word, else the
 * steps share the row by their words, else all step down together ([com.violinjourney.app.core.ui.components.SegmentFit]; 360 × 640
 * at the font 1.3). It works while a take runs. TalkBack hears a group «Статус» and each step by its word. [byWords] — lying, in the
 * column of 300: the steps share it by their words always, so «В репертуаре» stands in one line (spec 3.36.4).
 */
@Composable
internal fun StatusSwitch(status: PieceStatus, onIntent: (PieceIntent) -> Unit, modifier: Modifier = Modifier, byWords: Boolean = false) {
    val group = stringResource(Res.string.piece_field_status)
    val steps = PieceStatus.entries
    SegmentedSwitch(
        labels = steps.map { statusLabel(it) },
        selectedIndex = steps.indexOf(status),
        onSelect = { onIntent(PieceIntent.StatusSelected(steps[it])) },
        modifier = modifier,
        byWords = byWords,
        wholeWords = true,
        description = group,
    )
}
