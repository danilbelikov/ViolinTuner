package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.text.codePointLength
import com.violinjourney.app.core.text.takeCodePoints
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dialog_back
import com.violinjourney.app.shared.resources.dialog_cancel
import com.violinjourney.app.shared.resources.dialog_discard_changes
import com.violinjourney.app.shared.resources.dialog_discard_typed
import com.violinjourney.app.shared.resources.dialog_name_needed
import com.violinjourney.app.shared.resources.piece_form_discard_confirm
import com.violinjourney.app.shared.resources.piece_form_discard_title
import com.violinjourney.app.shared.resources.profile_name_counter
import org.jetbrains.compose.resources.stringResource

// The dialog of the redesign (spec 3.36.1, 5.29; components.html, «Диалоги»).
private val CardPaddingSide = 22.dp
private val CardPaddingTop = 22.dp
private val CardPaddingBottom = 14.dp
private val BinCircle = 44.dp
private val BinIcon = 24.dp
private val BinGap = 14.dp
private val TitleGap = 8.dp
private val ContentGap = 4.dp
private val ButtonsGap = 14.dp
private val ButtonSpacing = 4.dp
private val ButtonHeight = 48.dp
private val ButtonPadding = 16.dp
private val LabelGap = 6.dp
private val HelpGap = 6.dp
private val ReasonIcon = 15.dp
private val ReasonIconGap = 5.dp
private val FieldBorder = 1.5.dp
private const val BIN_CIRCLE_ALPHA = 0.16f
private const val DISABLED_ALPHA = 0.38f
private const val TABULAR_FIGURES = "tnum"

/** What the answer on the right is: the action of the dialog ([Accent]), something that deletes or replaces ([Danger]), or letting go ([Quiet]). */
enum class DialogTone { Accent, Danger, Quiet }

/**
 * The one dialog of the app (spec 3.36.1): a title, a line or two of text, words for buttons — the way back on the left, always in
 * the accent, the action on the right in its [confirmTone]. Nothing is filled: «Удалить» is a coral word, «Не сохранять» a quiet one.
 * [bin] puts the bin in a soft coral circle over a centred title — the sign of a deletion (3.16: the bin and the colour together).
 * [content] goes under the text: the field of [FieldDialog]. A confirm that cannot be pressed yet is dimmed, and its reason is said
 * in the dialog, never left to guess.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDialog(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    dismiss: String = stringResource(Res.string.dialog_cancel),
    confirmTone: DialogTone = DialogTone.Accent,
    confirmEnabled: Boolean = true,
    bin: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    // the window of AlertDialog: 280–560 dp wide, the pane title for TalkBack, dismissed by «назад» and a tap outside
    BasicAlertDialog(onDismissRequest = onDismiss) {
        AppDialogCard(
            title = title, confirm = confirm, onConfirm = onConfirm, onDismiss = onDismiss, text = text, dismiss = dismiss,
            confirmTone = confirmTone, confirmEnabled = confirmEnabled, bin = bin, content = content,
        )
    }
}

/** The card of [AppDialog] without its window: a preview cannot draw a window, and a screen never needs the card alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDialogCard(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismiss: String = stringResource(Res.string.dialog_cancel),
    confirmTone: DialogTone = DialogTone.Accent,
    confirmEnabled: Boolean = true,
    bin: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val danger = ViolinTheme.dangerSoft
    val align = if (bin) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.XL)
            .background(colors.surfaceContainerHigh)
            // a low landscape window, a large font, a keyboard: the buttons stay reachable
            .verticalScroll(rememberScrollState())
            .padding(start = CardPaddingSide, end = CardPaddingSide, top = CardPaddingTop, bottom = CardPaddingBottom),
        horizontalAlignment = if (bin) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (bin) {
            Box(Modifier.size(BinCircle).background(danger.copy(alpha = BIN_CIRCLE_ALPHA), CircleShape), contentAlignment = Alignment.Center) {
                AppIcon(AppIcons.Trash, contentDescription = null, tint = danger, size = BinIcon)
            }
            Spacer(Modifier.height(BinGap))
        }
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            color = colors.onSurface,
            textAlign = align,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold),
        )
        if (text != null) {
            Spacer(Modifier.height(TitleGap))
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                color = colors.onSurfaceVariant,
                textAlign = align,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
            )
        }
        if (content != null) {
            Spacer(Modifier.height(TitleGap + ContentGap))
            content()
        }
        Spacer(Modifier.height(ButtonsGap))
        // the way back on the left, the action on the right; two long words in a narrow dialog go one under the other
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonSpacing, Alignment.End),
        ) {
            DialogButton(dismiss, colors.primary, enabled = true, onClick = onDismiss)
            val tone = when (confirmTone) {
                DialogTone.Accent -> colors.primary
                DialogTone.Danger -> danger
                DialogTone.Quiet -> colors.onSurfaceVariant
            }
            DialogButton(confirm, tone, enabled = confirmEnabled, onClick = onConfirm)
        }
    }
}

@Composable
private fun DialogButton(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = ButtonHeight),
        shape = AppShapes.Control,
        contentPadding = PaddingValues(horizontal = ButtonPadding),
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = color.copy(alpha = DISABLED_ALPHA)),
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold))
    }
}

/**
 * «Не сохранять?» of a form (spec 3.36.1): an edit is no danger — no coral, no bin. «Вернуться» in the accent takes the player back to
 * the form, «Не сохранять» is said quietly; the text names what is lost, by the name it had before the edit.
 */
@Composable
fun DiscardDialog(loss: DiscardLoss, onDiscard: () -> Unit, onBack: () -> Unit) {
    AppDialog(
        title = stringResource(Res.string.piece_form_discard_title),
        text = when (loss) {
            is DiscardLoss.Changes -> stringResource(Res.string.dialog_discard_changes, loss.name)
            DiscardLoss.Typed -> stringResource(Res.string.dialog_discard_typed)
        },
        confirm = stringResource(Res.string.piece_form_discard_confirm),
        onConfirm = onDiscard,
        onDismiss = onBack,
        dismiss = stringResource(Res.string.dialog_back),
        confirmTone = DialogTone.Quiet,
    )
}

/**
 * A dialog with one field (spec 3.36.1): renaming a recording, the name of a section, the name of a preset. The field owns its text
 * (the state comes back a frame later and would take the cursor), starts with [initial] and the cursor at its end, and has the focus
 * from the start; [onValueChange] hears every change, [onConfirm] gets the text. [maxLength] cuts what is typed or pasted and shows
 * the counter under the field. While [confirmEnabled] is false the confirm is dimmed and «Нужно название» stands by the counter —
 * no silently grey button; [hint] says something under the field otherwise.
 */
@Composable
fun FieldDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onValueChange: (String) -> Unit = {},
    maxLength: Int? = null,
    confirmEnabled: Boolean = true,
    hint: String? = null,
    placeholder: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val start = maxLength?.let { initial.takeCodePoints(it) } ?: initial
        mutableStateOf(TextFieldValue(start, TextRange(start.length)))
    }
    val focus = remember { FocusRequester() }
    AppDialog(
        title = title,
        confirm = confirm,
        onConfirm = { onConfirm(value.text) },
        onDismiss = onDismiss,
        confirmEnabled = confirmEnabled,
    ) {
        DialogField(
            value = value,
            onValueChange = { next ->
                val text = maxLength?.let { next.text.takeCodePoints(it) } ?: next.text
                val cut = if (text == next.text) next else TextFieldValue(text, TextRange(text.length))
                val changed = cut.text != value.text
                value = cut
                if (changed) onValueChange(cut.text)
            },
            label = label,
            modifier = Modifier.focusRequester(focus),
            placeholder = placeholder,
            hint = hint,
            reason = if (confirmEnabled) null else stringResource(Res.string.dialog_name_needed),
            counter = maxLength?.let { stringResource(Res.string.profile_name_counter, value.text.codePointLength(), it) },
            capitalization = capitalization,
            onDone = { if (confirmEnabled) onConfirm(value.text) },
        )
        // in the composition of the dialog's window, after the field: the field is there to take the focus
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

/**
 * The field of a dialog (spec 5.29): its caption above it, as in the forms; 56 dp at a corner of 14 on the ground of the screen, a
 * frame of 1.5 lit in the accent while typed in; the placeholder in the third level of text. Under it one line: the [reason] a confirm
 * waits for — grey with its icon, never red, for it is no danger — or a [hint], and the [counter] on the right. Stateless.
 *
 * The caption and the line under the frame are drawn in the decoration of the text field, so they are the field for TalkBack and
 * VoiceOver: the field that takes the focus says what goes into it («Название раздела, поле ввода, Нужно название, 0 / 24»), as a
 * label of Material does. `OutlinedTextField` for a [TextFieldValue] cannot put its label above the frame, hence [BasicTextField]
 * with the decoration of the outlined field.
 */
@Composable
fun DialogField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    hint: String? = null,
    reason: String? = null,
    counter: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    onDone: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val tertiary = ViolinTheme.textTertiary
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val interaction = remember { MutableInteractionSource() }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceContainer,
        unfocusedContainerColor = colors.surfaceContainer,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outlineVariant,
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        cursorColor = colors.primary,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        interactionSource = interaction,
        decorationBox = { innerTextField ->
            Column(Modifier.fillMaxWidth()) {
                Text(label, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(LabelGap))
                // the frame is as wide as the dialog and 56 high at the least, as OutlinedTextField makes it
                Box(Modifier.fillMaxWidth().heightIn(min = OutlinedTextFieldDefaults.MinHeight), propagateMinConstraints = true) {
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = value.text,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = interaction,
                        placeholder = placeholder?.let { { Text(it, color = tertiary, maxLines = 1) } },
                        colors = fieldColors,
                        container = {
                            OutlinedTextFieldDefaults.Container(
                                enabled = true,
                                isError = false,
                                interactionSource = interaction,
                                colors = fieldColors,
                                shape = AppShapes.Control,
                                focusedBorderThickness = FieldBorder,
                                unfocusedBorderThickness = FieldBorder,
                            )
                        },
                    )
                }
                if (reason != null || hint != null || counter != null) {
                    Spacer(Modifier.height(HelpGap))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (reason != null) {
                                Row(
                                    // said aloud when it appears: the button beside it has just gone dim
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ReasonIconGap),
                                ) {
                                    AppIcon(AppIcons.Info, contentDescription = null, tint = colors.onSurfaceVariant, size = ReasonIcon)
                                    Text(reason, color = colors.onSurfaceVariant, style = small)
                                }
                            } else if (hint != null) {
                                Text(hint, color = colors.onSurfaceVariant, style = small)
                            }
                        }
                        if (counter != null) {
                            Text(counter, color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = small.copy(fontFeatureSettings = TABULAR_FIGURES))
                        }
                    }
                }
            }
        },
    )
}
