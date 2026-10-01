package com.violinjourney.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.domain.journey.JourneyStop
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.WholeText
import com.violinjourney.app.core.ui.components.WholeWords
import com.violinjourney.app.core.ui.components.WholeWordsFit
import com.violinjourney.app.core.ui.components.WordsAndEndDefaults
import com.violinjourney.app.core.ui.components.appButtonBeside
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.components.shortfallPlateFits
import com.violinjourney.app.core.ui.components.textsStandWhole
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.home.art.CARD_MAX_SCALE
import com.violinjourney.app.feature.home.art.ItemThumb
import com.violinjourney.app.feature.home.art.rememberHomeTime
import com.violinjourney.app.feature.journey.cityToOf
import com.violinjourney.app.feature.journey.sessionsInWords
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.shop_buy
import com.violinjourney.app.shared.resources.shop_from
import com.violinjourney.app.shared.resources.shop_left_after
import com.violinjourney.app.shared.resources.shop_missing
import com.violinjourney.app.shared.resources.shop_needs_chimney
import com.violinjourney.app.shared.resources.shop_no_place
import com.violinjourney.app.shared.resources.shop_owned
import com.violinjourney.app.shared.resources.shop_put
import com.violinjourney.app.shared.resources.shop_row_after
import com.violinjourney.app.shared.resources.shop_row_balance
import com.violinjourney.app.shared.resources.shop_row_place
import com.violinjourney.app.shared.resources.shop_row_to
import com.violinjourney.app.shared.resources.shop_standing
import com.violinjourney.app.shared.resources.shop_standing_outside
import com.violinjourney.app.shared.resources.shop_still_enough
import com.violinjourney.app.shared.resources.shop_still_more
import com.violinjourney.app.shared.resources.shop_take
import com.violinjourney.app.shared.resources.shop_try
import com.violinjourney.app.shared.resources.shop_waits_season
import kotlin.math.ceil
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * What the card of a thing says and offers (spec 3.36.7, «Карточка вещи»; 3.24, 3.29). Pure, with a test.
 *
 * - [chip] — under the name: «в комнате» / «у дома» for a thing standing in the home, «куплено» for one bought that cannot be put now
 *   (this home has no place for it, or it is the choice of its place and waits for its season);
 * - [placeNote] — under the value of «Место»: «нужен дом с трубой» / «нет места в этом доме», «ждёт декабря»;
 * - [money] — the rows about takts of a thing for sale: «После покупки — останется …» and «До Праги — всё ещё хватает / ещё …» while
 *   the takts are enough (no second row after the last city), «Баланс — …» while they are short («До Праги» would read two ways then);
 *   none for a gift and for what is owned;
 * - [buttons] — «Примерить» and «Купить · 300» in one row; «Купить · N» alone where the home has no place for it (it moves with its
 *   owner, 3.24); «Примерить» and «Забрать» for the gift; «Поставить» for a thing bought whose place is here and holds another;
 * - [sessions] — «примерно N занятий» under the row while the takts are short ([JourneyRules.sessionsLeft]); 0 — nothing.
 */
internal data class ItemCardPlan(
    val chip: Chip?,
    val placeNote: PlaceNote?,
    val money: Money?,
    val buttons: Buttons,
    val sessions: Int,
) {
    enum class Chip { STANDING, STANDING_OUTSIDE, OWNED }

    enum class PlaceNote { NEEDS_CHIMNEY, NO_PLACE, WAITS_SEASON }

    sealed interface Money {
        /** Enough: [left] after buying, and what that leaves for the road to the next city, if there is one. */
        data class After(val left: Long, val toNext: ToNext?) : Money

        /** Short: the purse as it is. */
        data class Balance(val balance: Long) : Money
    }

    /** The road to the next city ([stop]) after buying: still enough, or [takts] more to be earned for it. */
    sealed interface ToNext {
        val stop: String

        data class StillEnough(override val stop: String) : ToNext

        data class More(override val stop: String, val takts: Long) : ToNext
    }

    enum class Buttons { None, Put, TryAndBuy, Buy, TryAndTake }

    companion object {
        /** The place of a fireplace: a home without it lacks a chimney, not just a place. */
        private const val CHIMNEY_SLOT = "fire"

        /**
         * The card of [item] whose shelf says [tag]: [chosen] — it is the choice of its place ([ShelfTags.chosen]); [here] — the home
         * lived in has its place; [inSeason] — the tree in December; [balance] — the purse; [next] — the next city of the road, null
         * after the last one; [config] — the takts of a practice for «примерно N занятий».
         */
        fun of(
            item: HomeItem,
            tag: ShelfTag,
            chosen: Boolean,
            here: Boolean,
            inSeason: Boolean,
            balance: Long,
            next: JourneyStop?,
            config: JourneyConfig,
        ): ItemCardPlan {
            val placeNote = when {
                !here -> if (item.slot == CHIMNEY_SLOT) PlaceNote.NEEDS_CHIMNEY else PlaceNote.NO_PLACE
                !inSeason -> PlaceNote.WAITS_SEASON
                else -> null
            }
            val canPut = tag == ShelfTag.OWNED && here && !chosen
            val chip = when {
                tag == ShelfTag.STANDING -> if (item.outside) Chip.STANDING_OUTSIDE else Chip.STANDING
                tag == ShelfTag.OWNED && !canPut -> Chip.OWNED
                else -> null
            }
            val money = when {
                tag != ShelfTag.PRICE || item.price <= 0 -> null
                balance >= item.price -> {
                    val left = balance - item.price
                    Money.After(left, next?.let { if (left >= it.price) ToNext.StillEnough(it.id) else ToNext.More(it.id, it.price - left) })
                }
                else -> Money.Balance(balance)
            }
            val buttons = when (tag) {
                ShelfTag.STANDING, ShelfTag.LOCKED -> Buttons.None
                ShelfTag.OWNED -> if (canPut) Buttons.Put else Buttons.None
                ShelfTag.GIFT -> if (here) Buttons.TryAndTake else Buttons.Buy
                ShelfTag.PRICE -> if (here) Buttons.TryAndBuy else Buttons.Buy
            }
            val sessions = if (money is Money.Balance) JourneyRules.sessionsLeft(item.price - balance, config) else 0
            return ItemCardPlan(chip, placeNote, money, buttons, sessions)
        }

        /** The card of [item] in the shop of [ui] on [date]. */
        fun of(item: HomeItem, ui: HomeUi, date: LocalDate): ItemCardPlan = of(item, ui, ShelfTags(ui.home, ui.progress, ui.house, date), date)

        /** The card of [item] in the shop of [ui] on [date], its shelf told by [tags]. */
        fun of(item: HomeItem, ui: HomeUi, tags: ShelfTags, date: LocalDate): ItemCardPlan = of(
            item = item,
            tag = tags.of(item),
            chosen = tags.chosen(item),
            here = placeIsHere(item, ui.house),
            inSeason = HomeRules.inSeason(item, date),
            balance = ui.balance,
            next = JourneyRules.next(ui.progress),
            config = ui.config,
        )

        /** The home [house] has the place of [item] — and, for a pet, the place it lies at. */
        fun placeIsHere(item: HomeItem, house: String): Boolean =
            HomeRules.slotIn(item.slot, house) && item.at.let { at -> at == null || HomeRules.slotIn(at, house) }
    }
}

// The card of a thing (spec 5.29 R7, «Карточка вещи»): the thing large on the material of its shelf, 180; «из Вены» 13 / 700 accent; the
// name 22 / 800 in up to two lines; the chip of 28; the line about it 14; rows of 40 at least with a line over each; the buttons 14 under
// the rows, 10 apart, «Примерить» : «Купить» as 1 : 1.4; «примерно N занятий» 13, 6 under them.
private val CardPicture = 180.dp
private val CardBoardInset = 18.dp
private val CardThumbTop = 12.dp
private val FromTop = 14.dp
private val NameTop = 2.dp
private val NameSize = 22.sp
private const val NAME_SP = 22f
private const val NAME_LEAST_SP = 18f
private const val NAME_LINES = 2
private val ChipTop = 8.dp
private val ChipHeight = 28.dp
private val ChipSide = 10.dp
private val NoteTop = 6.dp
private val RowsTop = 10.dp
private val RowMinHeight = 40.dp
private val RowPadding = 9.dp
private val RowLine = 1.dp
private val RowGap = 12.dp

// A row «ключ — значение»: the key 14 in the second colour, the value 14 / 700 tabular in up to two lines, the note of the place 13.
private const val KEY_SP = 14f
private const val VALUE_SP = 14f
private const val NOTE_SP = 13f
private val RowLineHeight = 20.sp
private val NoteLineHeight = 18.sp

private val ButtonsTop = 14.dp
private val ButtonsGap = 10.dp
private val SessionsTop = 6.dp

/** «Купить» is this much wider than «Примерить» beside it (spec 5.29 R7: 1 : 1.4). */
private const val BUY_WEIGHT = 1.4f

/** «Примерить» steps down to this in its share of the row, then the two stand one under the other (no word is broken). */
private const val OUTLINE_LEAST_SP = 13f

/** The words of «Купить · 300» step down to this before they would be cut — the least of a button of one line (5.29 R7). */
private const val BUY_LEAST_SP = 15f

private const val TABULAR_FIGURES = "tnum"

/** The plan of the card of [item] on [today], worked out again only when the home, the purse or the day change. */
@Composable
private fun rememberCardPlan(item: HomeItem, ui: HomeUi, today: LocalDate): ItemCardPlan =
    remember(item, ui.home, ui.progress, ui.house, ui.config, today) { ItemCardPlan.of(item, ui, today) }

/**
 * The card of a thing (spec 3.36.7) — what a sheet of R1 holds over its pinned buttons ([ItemCardButtons]): the thing large and alive on
 * the material of its shelf and on its board (3.29), «из Вены» over the name where it is brought, the name — a heading, in up to two
 * lines, its words never broken —, the chip of its state, the line about it, and the rows «Место», «После покупки», «До Праги» or
 * «Баланс», each one phrase for a reader. [today] — the date the tree's season is told by: the phone's, a fixed one in a preview.
 */
@Composable
fun ItemCardContent(item: HomeItem, ui: HomeUi, modifier: Modifier = Modifier, today: LocalDate = rememberHomeTime().date) {
    val colors = MaterialTheme.colorScheme
    val plan = rememberCardPlan(item, ui, today)
    Column(modifier.fillMaxWidth()) {
        val material = ShelfMaterial.of(item.group)
        Box(Modifier.fillMaxWidth().height(CardPicture).clip(HomeCard).drawBehind { material.paint(this) }) {
            Board(material, Modifier.align(Alignment.BottomCenter).padding(bottom = CardBoardInset).fillMaxWidth().height(BoardHeight))
            Box(Modifier.fillMaxSize().padding(top = CardThumbTop, bottom = CardBoardInset + BoardHeight)) { ItemThumb(item, alive = true, maxScale = CARD_MAX_SCALE) }
        }
        val from = item.from?.let { stringResource(Res.string.shop_from, cityToOf(JourneyRoute.indexOf(it))) }
        if (from != null) {
            Text(
                from,
                modifier = Modifier.padding(top = FromTop),
                color = colors.primary,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
            )
        }
        Text(
            text = itemName(item.id),
            modifier = Modifier.padding(top = if (from != null) NameTop else FromTop).semantics { heading() },
            color = colors.onSurface,
            autoSize = remember { WholeWordsFit(NAME_SP, NAME_LEAST_SP) },
            maxLines = NAME_LINES,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = NameSize, lineHeight = 1.25.em, fontWeight = FontWeight.ExtraBold),
        )
        plan.chip?.let { chip ->
            StatusChip(
                stringResource(
                    when (chip) {
                        ItemCardPlan.Chip.STANDING -> Res.string.shop_standing
                        ItemCardPlan.Chip.STANDING_OUTSIDE -> Res.string.shop_standing_outside
                        ItemCardPlan.Chip.OWNED -> Res.string.shop_owned
                    },
                ),
                Modifier.padding(top = ChipTop),
            )
        }
        itemNote(item.id).takeIf { it.isNotEmpty() }?.let { note ->
            Text(
                note,
                modifier = Modifier.padding(top = NoteTop),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            )
        }
        Column(Modifier.padding(top = RowsTop)) {
            CardRow(
                key = stringResource(Res.string.shop_row_place),
                value = slotName(item.at ?: item.slot),
                note = plan.placeNote?.let { placeNote ->
                    stringResource(
                        when (placeNote) {
                            ItemCardPlan.PlaceNote.NEEDS_CHIMNEY -> Res.string.shop_needs_chimney
                            ItemCardPlan.PlaceNote.NO_PLACE -> Res.string.shop_no_place
                            ItemCardPlan.PlaceNote.WAITS_SEASON -> Res.string.shop_waits_season
                        },
                    )
                },
            )
            when (val money = plan.money) {
                is ItemCardPlan.Money.After -> {
                    CardRow(stringResource(Res.string.shop_row_after), stringResource(Res.string.shop_left_after, Formats.takts(money.left)))
                    money.toNext?.let { road ->
                        CardRow(
                            key = stringResource(Res.string.shop_row_to, cityToOf(JourneyRoute.indexOf(road.stop))),
                            value = when (road) {
                                is ItemCardPlan.ToNext.StillEnough -> stringResource(Res.string.shop_still_enough)
                                is ItemCardPlan.ToNext.More -> stringResource(Res.string.shop_still_more, Formats.takts(road.takts))
                            },
                        )
                    }
                }
                is ItemCardPlan.Money.Balance -> CardRow(stringResource(Res.string.shop_row_balance), Formats.takts(money.balance))
                null -> Unit
            }
        }
    }
}

/** Whether the card has buttons at all: a thing standing in the home, or bought and waiting, has none. */
internal fun ItemCardPlan.hasButtons(): Boolean = buttons != ItemCardPlan.Buttons.None

/**
 * The buttons of the card of a thing, pinned at the bottom of its sheet (spec 3.36.7): «Примерить» and «Купить · 300» — or the plate
 * «не хватает N» — in one row, «Купить · N» alone where this home has no place for the thing, «Примерить» and «Забрать» for the gift,
 * «Поставить» for a thing bought whose place holds another; under the row «примерно N занятий» while the takts are short. 48 in a
 * window no higher than 360. Buying is one press, as it was; a swipe of the sheet never buys.
 */
@Composable
fun ItemCardButtons(item: HomeItem, ui: HomeUi, onIntent: (HomeIntent) -> Unit, today: LocalDate = rememberHomeTime().date) {
    val plan = rememberCardPlan(item, ui, today)
    // a thing standing in the home, or bought and waiting, has nothing to press: nothing at all, not an empty row
    if (!plan.hasButtons()) return
    val compact = currentDockMetrics().compact
    val label = buyLabel(item)
    val onBuy = { onIntent(HomeIntent.BuyClicked) }
    Column(Modifier.fillMaxWidth().padding(top = ButtonsTop)) {
        when (plan.buttons) {
            ItemCardPlan.Buttons.None -> Unit
            ItemCardPlan.Buttons.Put -> AppButton(
                text = stringResource(Res.string.shop_put),
                onClick = {
                    onIntent(HomeIntent.Placed(item.slot, item.id))
                    onIntent(HomeIntent.CardClosed)
                },
                modifier = Modifier.fillMaxWidth(),
                compact = compact,
                oneLine = true,
            )
            ItemCardPlan.Buttons.Buy -> BuyButton(item.price, ui.balance, label, onBuy, Modifier.fillMaxWidth(), compact)
            ItemCardPlan.Buttons.TryAndBuy, ItemCardPlan.Buttons.TryAndTake -> OutlineAndBuy(
                outline = stringResource(Res.string.shop_try),
                onOutline = { onIntent(HomeIntent.TryClicked) },
                price = item.price,
                balance = ui.balance,
                label = label,
                onBuy = onBuy,
                compact = compact,
                byWord = false,
            )
        }
        if (plan.sessions > 0) {
            Text(
                sessionsInWords(plan.sessions),
                modifier = Modifier.fillMaxWidth().padding(top = SessionsTop),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            )
        }
    }
}

/** «Купить · 300», or «Забрать» for what costs nothing — the gift. */
@Composable
internal fun buyLabel(item: HomeItem): String =
    if (item.price == 0) stringResource(Res.string.shop_take) else stringResource(Res.string.shop_buy, Formats.takts(item.price.toLong()))

/**
 * An outline and what buys beside it, 10 apart and of one height (spec 3.36.7, 5.29 R7): in the card — «Примерить» and «Купить · 300»
 * (or «Забрать», or the plate «не хватает N») as 1 : 1.4 — and in the try-on — «Убрать» by its word ([byWord]) and the rest. No word
 * breaks and no number is cut beside the other: «Примерить» steps down to [OUTLINE_LEAST_SP] in its share, «Купить · …» may take
 * [BUY_LEAST_SP] and the plate its 14 sp; where one of them does not stand whole so, the two stand one under the other on the whole
 * width — what buys first, as the main answer of a sheet stands over the second. [outlineColor] — the frame of the outline: white at
 * 35 % on the veil of the try-on.
 */
@Composable
internal fun OutlineAndBuy(
    outline: String,
    onOutline: () -> Unit,
    price: Int,
    balance: Long,
    label: String,
    onBuy: () -> Unit,
    compact: Boolean,
    byWord: Boolean,
    modifier: Modifier = Modifier,
    outlineColor: Color = Color.Unspecified,
) {
    val missing = shopMissingWords(price, balance)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // by its word: the whole row is its share, so it stands at the size of its style and as wide as its word
        val share = if (byWord) maxWidth else (maxWidth - ButtonsGap) / (1f + BUY_WEIGHT)
        val fitted = appButtonBeside(outline, share, AppButtonStyle.Outline, compact, minSp = OUTLINE_LEAST_SP)
        val buyRoom = maxWidth - ButtonsGap - (if (byWord) fitted?.width ?: maxWidth else share)
        val buyFits = if (missing == null) {
            appButtonBeside(label, buyRoom, AppButtonStyle.Main, compact, minSp = BUY_LEAST_SP) != null
        } else {
            shortfallPlateFits(missing, buyRoom, compact, leading = PlateSign)
        }
        if (fitted != null && buyFits) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ButtonsGap)) {
                AppButton(
                    text = outline,
                    onClick = onOutline,
                    modifier = (if (byWord) Modifier else Modifier.weight(1f)).fillMaxHeight(),
                    style = AppButtonStyle.Outline,
                    compact = compact,
                    fontSize = fitted.fontSize,
                    outline = outlineColor,
                )
                BuyButton(price, balance, label, onBuy, Modifier.weight(if (byWord) 1f else BUY_WEIGHT).fillMaxHeight(), compact)
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ButtonsGap)) {
                BuyButton(price, balance, label, onBuy, Modifier.fillMaxWidth(), compact)
                AppButton(
                    text = outline,
                    onClick = onOutline,
                    modifier = Modifier.fillMaxWidth(),
                    style = AppButtonStyle.Outline,
                    compact = compact,
                    oneLine = true,
                    outline = outlineColor,
                )
            }
        }
    }
}

/** «не хватает 428» of the plate [BuyButton] would show for [price] out of [balance]; null — the takts are enough. */
@Composable
private fun shopMissingWords(price: Int, balance: Long): String? =
    if (balance >= price) null else stringResource(Res.string.shop_missing, Formats.takts(price - balance))

/** «в комнате», «у дома», «куплено»: a chip of 28 on the soft accent, 12 sp / 800 in the accent — said as a word, not by colour alone. */
@Composable
private fun StatusChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = ChipHeight)
            .clip(CircleShape)
            .background(ViolinTheme.accentSoft)
            .padding(horizontal = ChipSide),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

/**
 * A row «ключ — значение» of the card (spec 5.29 R7): 40 at least, a line of 1 over it; the [key] 14 in the second colour on the left,
 * the [value] 14 / 700, tabular, on the right in up to two lines, and the [note] of the place, 13, under it — side by side, or the value
 * under the key where they do not stand whole beside each other ([CardRowFit]). For a reader the row is one phrase: «После покупки,
 * останется 47 584».
 */
@Composable
private fun CardRow(key: String, value: String, note: String? = null) {
    val colors = MaterialTheme.colorScheme
    val line = colors.outlineVariant
    val said = listOfNotNull(key, value, note).joinToString(", ")
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .drawBehind { drawLine(line, Offset(0f, RowLine.toPx() / 2), Offset(size.width, RowLine.toPx() / 2), RowLine.toPx()) }
            .clearAndSetSemantics { contentDescription = said }
            .padding(vertical = RowPadding),
        contentAlignment = Alignment.CenterStart,
    ) { KeyAndValue(key, value, note) }
}

/**
 * The key at the start of a row and the value at its end (spec 5.29 R7), as [CardRowFit] stands them: beside — the key in the room it
 * leaves it, the value and the note in what the key leaves after [RowGap], aligned to the end, all from the top of the row; or under — the
 * key on the whole width and the value and the note at the end of the row under it. The key steps down from 14 sp, where it would break
 * a word, to [CardRowFit.KEY_LEAST_SP] — its size is found where it is laid out ([WholeWordsFit]), as [CardRowFit] measured it.
 */
@Composable
private fun KeyAndValue(key: String, value: String, note: String?) {
    val colors = MaterialTheme.colorScheme
    val keyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = KEY_SP.sp, lineHeight = RowLineHeight)
    val valueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = VALUE_SP.sp,
        lineHeight = RowLineHeight,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    val noteStyle = MaterialTheme.typography.bodySmall.copy(fontSize = NOTE_SP.sp, lineHeight = NoteLineHeight)
    val measurer = rememberTextMeasurer()
    val whole = remember(key, value, note, keyStyle, valueStyle, noteStyle) {
        WholeText(key, keyStyle, leastSp = CardRowFit.KEY_LEAST_SP) to
            listOfNotNull(WholeText(value, valueStyle, maxLines = CardRowFit.VALUE_LINES), note?.let { WholeText(it, noteStyle) })
    }
    Layout(
        content = {
            Text(key, color = colors.onSurfaceVariant, style = keyStyle, autoSize = remember { WholeWordsFit(KEY_SP, CardRowFit.KEY_LEAST_SP) })
            Text(
                value,
                color = colors.onSurface,
                textAlign = TextAlign.End,
                maxLines = CardRowFit.VALUE_LINES,
                overflow = TextOverflow.Ellipsis,
                style = valueStyle,
            )
            if (note != null) Text(note, color = colors.onSurfaceVariant, textAlign = TextAlign.End, style = noteStyle)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val gap = RowGap.roundToPx()
        // a row is laid out in its card's width; asked without one (an intrinsic size), the key and the value stand side by side whole
        val bounded = constraints.hasBoundedWidth
        val width = if (bounded) {
            constraints.maxWidth
        } else {
            measurables.first().maxIntrinsicWidth(Constraints.Infinity) + gap + measurables.drop(1).maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        }
        val room = if (bounded) CardRowFit.keyRoom(measurer, whole.first, whole.second, width, gap, this, layoutDirection) else Constraints.Infinity
        val keyPlaced = measurables.first().measure(Constraints(maxWidth = room ?: width))
        val rest = if (room != null) (width - keyPlaced.width - gap).coerceAtLeast(0) else width
        val values = measurables.drop(1).map { it.measure(Constraints(maxWidth = rest)) }
        val valuesTop = if (room != null) 0 else keyPlaced.height + WordsAndEndDefaults.UnderGap.roundToPx()
        val height = maxOf(keyPlaced.height, valuesTop + values.sumOf { it.height })
        layout(width, height) {
            keyPlaced.place(0, 0)
            var y = valuesTop
            values.forEach {
                it.place(width - it.width, y)
                y += it.height
            }
        }
    }
}

/**
 * How a row «ключ — значение» of the card stands (spec 5.29 R7, «Карточка вещи»: «число не режется», the key «переносится по
 * пробелу»; the review of stage 119). Pure over the text engine, with a test on iOS.
 *
 * The value keeps the room of its widest part that cannot break — the number and the word glued to it by a non-breaking space:
 * «ещё 1 128», «mancano 5 528» — and the key takes no more than that leaves, nor more than [KEY_SHARE] of the row. Side by side where
 * the key keeps its words whole there — every line but the last ends at a space, at 14 sp or smaller down to [KEY_LEAST_SP]
 * ([WholeWords]: on iOS a line breaks after a hyphen as well, «До Санкт-» / «Петербурга», where Android goes on at the space) — and the
 * value and the note keep theirs in what the key leaves, on the value's [VALUE_LINES]; else the value goes under the key, and nothing
 * is broken to keep them side by side. On a phone of 360 at the font 1.3 «Per San Pietroburgo» beside «mancano 1 900» left the number
 * 134 dp of the 138 it needs, and it broke inside.
 */
internal object CardRowFit {
    /** The key of a row keeps at most this share of it: the value — up to two lines — is what the row is for. */
    const val KEY_SHARE = 0.6f

    /** The key steps down to this before the value goes under it. */
    const val KEY_LEAST_SP = 12f

    /** The value stands on at most this many lines. */
    const val VALUE_LINES = 2

    /**
     * The room, px, the [key] takes beside the [values] — the value and the note of the place — in a row [width] px wide with [gap]
     * between them; null — the values go under the key.
     */
    fun keyRoom(measurer: TextMeasurer, key: WholeText, values: List<WholeText>, width: Int, gap: Int, density: Density, direction: LayoutDirection): Int? {
        val valuesLeast = values.maxOfOrNull { leastWidth(measurer, it, density, direction) } ?: 0
        val room = minOf((width * KEY_SHARE).toInt(), width - gap - valuesLeast)
        if (room <= 0 || !textsStandWhole(measurer, listOf(key), room, density, direction)) return null
        val keyWidth = laid(measurer, key, room, density, direction).let { at -> at(WholeWords.largest(key.style.fontSize.value, key.leastSp, at)) }.size.width
        return room.takeIf { textsStandWhole(measurer, values, width - gap - keyWidth, density, direction) }
    }

    /** The widest part of [text] no line may break: a word, or words glued by non-breaking spaces. */
    private fun leastWidth(measurer: TextMeasurer, text: WholeText, density: Density, direction: LayoutDirection): Int =
        ceil(measurer.measure(text.text, text.style, layoutDirection = direction, density = density).multiParagraph.intrinsics.minIntrinsicWidth).toInt()

    /** [text] laid out [room] px wide at a size, as its `Text` lays it out there. */
    private fun laid(measurer: TextMeasurer, text: WholeText, room: Int, density: Density, direction: LayoutDirection): (Float) -> TextLayoutResult = { sizeSp ->
        measurer.measure(
            text = text.text,
            style = text.style.copy(fontSize = sizeSp.sp),
            overflow = TextOverflow.Ellipsis,
            maxLines = text.maxLines,
            constraints = Constraints(maxWidth = room),
            layoutDirection = direction,
            density = density,
        )
    }
}
