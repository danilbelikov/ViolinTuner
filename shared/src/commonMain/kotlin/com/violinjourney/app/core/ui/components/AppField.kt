package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The field of the redesign (spec 5.29; components.html, «Диалоги»): 56 at a corner of 14, the caption above it, one line under it.
private val LabelGap = 6.dp
private val HelpGap = 6.dp
private val HelpSide = 2.dp
private val HelpSpacing = 12.dp
private val ErrorIcon = 15.dp
private val ErrorIconGap = 5.dp
private val FieldBorder = 1.5.dp
private const val DISABLED_ALPHA = 0.38f

/** The counter of a field in a sheet (5.29 R3: «0 / 24» 12 sp). */
private const val SHEET_COUNTER_SIZE = 12
private const val TABULAR_FIGURES = "tnum"

/** What is measured for the height of one line of a style. */
private const val ONE_LINE = " "

/**
 * The one text field of the app (spec 3.36.1, 5.29): its caption [label] above it, as in the forms; 56 dp high at the least at a
 * corner of 14 on surfaceContainer, a frame of 1.5 in both states — outlineVariant, lit in the accent while typed in; the text
 * 16 sp, the [placeholder] in the third level of text (4.7 : 1 on surfaceContainer). Under it one line: on the left the [error]
 * — grey with its icon, never red, for a mistake of input is no danger; the frame does not redden and the field is not marked
 * as an error — or a [hint]; on the right the [counter] in tabular figures. The error line is said aloud when it appears.
 *
 * Stateless: the caller holds the text, the focus and any cut of the length ([FieldDialog], the forms of R4 and R9).
 *
 * The caption and the line under the frame are drawn in the decoration of the text field, so they are the field for TalkBack
 * and VoiceOver: the field that takes the focus says what goes into it («Название раздела, поле ввода, Нужно название,
 * 0 / 24»), as a label of Material does, and a touch on the caption puts the focus in the field. `OutlinedTextField` for a
 * [TextFieldValue] cannot put its label above the frame, hence [BasicTextField] with the decoration of the outlined field.
 * [enabled] false dims the whole field to 0.38 in its own colours.
 *
 * [showLabel] false — the caption is not drawn, its place is taken by the [placeholder] in the field; the field is still named by it
 * for TalkBack and VoiceOver («Имя» of «Имя и фото», R3). [inSheet] — the field of a sheet (5.29 R3): on surfaceContainerHigh, its
 * frame unseen until it is typed in, the placeholder in the second level of text (the third reads 4.1 : 1 on surfaceContainerHigh),
 * the counter 12 sp.
 *
 * [labelDescription] — how TalkBack and VoiceOver say the caption where the drawn one is made of parts: «Композитор · необязательно»
 * on screen, «Композитор, необязательно» aloud (the forms of R4, 3.36.4); null — the caption as drawn.
 *
 * [typedLine] — where the field notes the line being typed, for a form that keeps it on a keyboard too high for the whole field.
 */
@Composable
fun AppField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    hint: String? = null,
    error: String? = null,
    counter: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    showLabel: Boolean = true,
    inSheet: Boolean = false,
    labelDescription: String? = null,
    typedLine: TypedLine? = null,
) {
    val colors = MaterialTheme.colorScheme
    if (typedLine != null) {
        val density = LocalDensity.current
        // the cursor, and the paddings the frame puts over and under its text — those of OutlinedTextField, the field keeps them
        val frame = OutlinedTextFieldDefaults.contentPadding()
        SideEffect {
            typedLine.cursor = value.selection.max
            typedLine.above = with(density) { frame.calculateTopPadding().toPx() }
            typedLine.below = with(density) { frame.calculateBottomPadding().toPx() }
        }
    }
    val placeholderColor = if (inSheet) colors.onSurfaceVariant else ViolinTheme.textTertiary
    val container = if (inSheet) colors.surfaceContainerHigh else colors.surfaceContainer
    val restingBorder = if (inSheet) Color.Transparent else colors.outlineVariant
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val counterStyle = if (inSheet) small.copy(fontSize = SHEET_COUNTER_SIZE.sp) else small
    val interaction = remember { MutableInteractionSource() }
    // the disabled field keeps its colours: it is dimmed as a whole, as a disabled button is
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = container,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = restingBorder,
        disabledBorderColor = restingBorder,
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        disabledTextColor = colors.onSurface,
        disabledPlaceholderColor = placeholderColor,
        cursorColor = colors.primary,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .then(if (typedLine == null) Modifier else Modifier.onPlaced { typedLine.field = it })
            .fillMaxWidth()
            // the caption not drawn still names the field
            .then(if (showLabel) Modifier else Modifier.semantics { contentDescription = labelDescription ?: label })
            .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)),
        enabled = enabled,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        textStyle = typedStyle().copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        onTextLayout = { typedLine?.layout = it },
        decorationBox = { innerTextField ->
            Column(Modifier.fillMaxWidth()) {
                if (showLabel) {
                    Text(
                        text = label,
                        // said as its own words where it has them; still a part of the field, as the caption drawn is
                        modifier = if (labelDescription == null) Modifier else Modifier.clearAndSetSemantics { text = AnnotatedString(labelDescription) },
                        color = colors.onSurfaceVariant,
                        style = captionStyle(),
                    )
                    Spacer(Modifier.height(LabelGap))
                }
                // the frame is as wide as the field and 56 high at the least, as OutlinedTextField makes it
                Box(Modifier.fillMaxWidth().heightIn(min = OutlinedTextFieldDefaults.MinHeight), propagateMinConstraints = true) {
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = value.text,
                        innerTextField = if (typedLine == null) {
                            innerTextField
                        } else {
                            // where the text stands in the field; the constraints of the frame reach it as they are
                            { Box(Modifier.onPlaced { typedLine.text = it }, propagateMinConstraints = true) { innerTextField() } }
                        },
                        enabled = enabled,
                        singleLine = singleLine,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = interaction,
                        placeholder = placeholder?.let { { Text(it, color = placeholderColor, maxLines = if (singleLine) 1 else Int.MAX_VALUE) } },
                        colors = fieldColors,
                        container = {
                            OutlinedTextFieldDefaults.Container(
                                enabled = enabled,
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
                if (error != null || hint != null || counter != null) {
                    Spacer(Modifier.height(HelpGap))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = HelpSide),
                        horizontalArrangement = Arrangement.spacedBy(HelpSpacing),
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (error != null) {
                                Row(
                                    // said aloud when it appears: the button it stands for has just gone dim
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ErrorIconGap),
                                ) {
                                    AppIcon(AppIcons.Info, contentDescription = null, tint = colors.onSurfaceVariant, size = ErrorIcon)
                                    Text(error, color = colors.onSurfaceVariant, style = small)
                                }
                            } else if (hint != null) {
                                Text(hint, color = colors.onSurfaceVariant, style = small)
                            }
                        }
                        if (counter != null) {
                            Text(counter, color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = counterStyle.copy(fontFeatureSettings = TABULAR_FIGURES))
                        }
                    }
                }
            }
        },
    )
}

/**
 * The least height of an [AppField] with its caption (spec 5.29 R4: the forms lying over a keyboard): one line of the caption, 6 under
 * it and the frame of one line of text — the line with the paddings of the frame above and under it, 56 at the least; 20 + 6 + 56 =
 * 82 at the font of 1.0, more with a larger one. Laid out by the styles and numbers of the field itself, not guessed: a field of more
 * lines or with a line under it is higher, none is lower.
 */
@Composable
fun appFieldLeastHeight(): Dp {
    val measurer = rememberTextMeasurer()
    val caption = captionStyle()
    val typed = typedStyle()
    val density = LocalDensity.current
    return remember(measurer, caption, typed, density) {
        with(density) {
            val captionLine = measurer.measure(ONE_LINE, caption).size.height.toDp()
            val textLine = measurer.measure(ONE_LINE, typed).size.height.toDp()
            // the paddings the frame puts around its text — those of OutlinedTextField, the field keeps them; the placeholder is a
            // line of bodyLarge, no higher than the typed one
            val frame = OutlinedTextFieldDefaults.contentPadding()
            val frameHeight = maxOf(OutlinedTextFieldDefaults.MinHeight, frame.calculateTopPadding() + textLine + frame.calculateBottomPadding())
            captionLine + LabelGap + frameHeight
        }
    }
}

/**
 * Where the line being typed stands in an [AppField] (spec 5.29 R4: a form lying over a keyboard too high for the whole field keeps
 * this line on the keyboard, and the caption goes up out of sight): the line of the cursor with the paddings of the frame over and
 * under it, in the field's own coordinates. The field notes what it takes while it is laid out — where it and its text stand, the
 * layout of the text, the cursor; [bounds] puts them together when asked, null before the field has been laid out. Not a state: the
 * cursor moving recomposes nothing.
 */
class TypedLine {
    internal var field: LayoutCoordinates? = null
    internal var text: LayoutCoordinates? = null
    internal var layout: TextLayoutResult? = null
    internal var cursor = 0
    internal var above = 0f
    internal var below = 0f

    /** The line of the cursor with the paddings of the frame over and under it, in pixels of the field; null — not laid out yet. */
    fun bounds(): Rect? {
        val field = field?.takeIf { it.isAttached } ?: return null
        val text = text?.takeIf { it.isAttached } ?: return null
        val layout = layout ?: return null
        val line = layout.getCursorRect(cursor.coerceIn(0, layout.layoutInput.text.length))
        val top = field.localPositionOf(text, Offset.Zero).y
        return Rect(0f, top + line.top - above, field.size.width.toFloat(), top + line.bottom + below)
    }
}

/** The caption over a field: 13 sp / 700. */
@Composable
@ReadOnlyComposable
private fun captionStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)

/** What is typed into a field: 16 sp on a line of 24. */
@Composable
@ReadOnlyComposable
private fun typedStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
