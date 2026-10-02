package com.violinjourney.app.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.KindLook
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.drawMiniSign
import com.violinjourney.app.core.ui.theme.KindColors
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_kind_lesson
import com.violinjourney.app.shared.resources.event_kind_lesson_word
import com.violinjourney.app.shared.resources.event_kind_other
import com.violinjourney.app.shared.resources.event_kind_other_word
import com.violinjourney.app.shared.resources.event_kind_performance
import com.violinjourney.app.shared.resources.event_kind_performance_word
import com.violinjourney.app.shared.resources.event_kind_rehearsal
import com.violinjourney.app.shared.resources.event_kind_rehearsal_word
import com.violinjourney.app.shared.resources.session_take_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The sign of a kind as an icon of 24 of the set (spec 3.36.9, 5.29 R9). */
fun kindIcon(sign: KindSign): ImageVector = when (sign) {
    KindSign.LESSON -> AppIcons.KindLesson
    KindSign.REHEARSAL -> AppIcons.KindRehearsal
    KindSign.PERFORMANCE -> AppIcons.KindPerformance
    KindSign.OTHER -> AppIcons.KindOther
    KindSign.BOOK -> AppIcons.KindBook
    KindSign.HAT -> AppIcons.KindHat
    KindSign.KEYS -> AppIcons.KindKeys
    KindSign.MASK -> AppIcons.KindMask
    KindSign.TICKET -> AppIcons.KindTicket
    KindSign.CHAT -> AppIcons.KindChat
    KindSign.HEART -> AppIcons.KindHeart
    KindSign.MOON -> AppIcons.KindMoon
    KindSign.LEAF -> AppIcons.KindLeaf
    KindSign.BOLT -> AppIcons.KindBolt
    KindSign.BOWTIE -> AppIcons.KindBowtie
    KindSign.ARC -> AppIcons.KindArc
}

/** The colours of the number a kind keeps: of its mark, and of its sign on its plate. */
@Composable
@ReadOnlyComposable
fun kindColors(look: KindLook): KindColors = ViolinTheme.eventsColors.of(look.color)

/**
 * The name of a kind (spec 3.35): of a built-in one, a word of the interface — «Урок», «Выступление»; of one's own, as it was written.
 * A kind of one's own without its name — gone — is «Другое».
 */
@Composable
fun eventKindName(kind: KindRef, ownName: String?): String = when (kind) {
    is KindRef.BuiltIn -> stringResource(builtInKindName(kind.kind))
    is KindRef.Custom -> ownName ?: stringResource(builtInKindName(BuiltInKind.OTHER))
}

@Composable
fun eventKindName(kind: EventKind): String = eventKindName(kind.ref, kind.ownName)

/**
 * The word of a kind in a sentence (spec 3.36.9, decision 12): a string of its own in every language for a built-in kind — «урок»,
 * «Unterricht» — and the name as written for one of one's own, never lowered: «Оркестр ДК» stays as it is.
 */
@Composable
fun eventKindWord(kind: KindRef, ownName: String?): String = when (kind) {
    is KindRef.BuiltIn -> stringResource(wordOf(kind.kind))
    is KindRef.Custom -> ownName ?: stringResource(wordOf(BuiltInKind.OTHER))
}

@Composable
fun eventKindWord(kind: EventKind): String = eventKindWord(kind.ref, kind.ownName)

/** What an event is called where it stands by itself — a row of the sheet of the day: its title, or the name of its kind («Урок»). */
@Composable
fun eventNameOf(name: EventName): String = when (name) {
    is EventName.Titled -> name.title
    is EventName.OfKind -> eventKindName(name.kind, name.ownName)
}

/** What an event is called in a sentence — «Завтра в 17:00 — урок»: its title, or the word of its kind. */
@Composable
fun eventWordOf(name: EventName): String = when (name) {
    is EventName.Titled -> name.title
    is EventName.OfKind -> eventKindWord(name.kind, name.ownName)
}

/**
 * What a recording of [event] is called until it is given a name of its own (spec 3.35, 3.36.9): the name of the event and its date —
 * «Осенний концерт · 24 октября», «Урок · 21 сентября». It follows the event: renamed, the recording is renamed with it.
 */
@Composable
fun eventRecordTitle(event: SessionEvent): String =
    stringResource(Res.string.session_take_title, eventNameOf(event.name), Formats.dayAndMonth(event.date))

/** The string of the name of a built-in kind: «Урок». */
internal fun builtInKindName(kind: BuiltInKind): StringResource = when (kind) {
    BuiltInKind.LESSON -> Res.string.event_kind_lesson
    BuiltInKind.REHEARSAL -> Res.string.event_kind_rehearsal
    BuiltInKind.PERFORMANCE -> Res.string.event_kind_performance
    BuiltInKind.OTHER -> Res.string.event_kind_other
}

private fun wordOf(kind: BuiltInKind): StringResource = when (kind) {
    BuiltInKind.LESSON -> Res.string.event_kind_lesson_word
    BuiltInKind.REHEARSAL -> Res.string.event_kind_rehearsal_word
    BuiltInKind.PERFORMANCE -> Res.string.event_kind_performance_word
    BuiltInKind.OTHER -> Res.string.event_kind_other_word
}

/**
 * The sign of a kind on its plate (spec 5.29 R9): the mark of the kind at 18 % over [ground] — what the plate lies on — and the sign
 * in the colour it has on its plate. Silent: the words beside it name the kind.
 */
@Composable
fun KindSignPlate(look: KindLook, plate: Dp, corner: Dp, sign: Dp, ground: Color, modifier: Modifier = Modifier) {
    val colors = ViolinTheme.eventsColors
    Box(
        modifier = modifier.size(plate).background(colors.plate(look.color, ground), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(kindIcon(look.sign), contentDescription = null, size = sign, tint = colors.of(look.color).onPlate)
    }
}

/** A mini sign of [size] in the colour of its kind (the legend, «ещё N»): drawn, never laid out of parts. Silent. */
@Composable
fun MiniSign(look: KindLook, size: Dp, modifier: Modifier = Modifier) {
    val color = kindColors(look).color
    Spacer(modifier.size(size).drawBehind { drawMiniSign(look.sign, color, Offset.Zero, this.size.minDimension) })
}
