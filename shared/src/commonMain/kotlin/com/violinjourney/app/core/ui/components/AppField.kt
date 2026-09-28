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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
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
private const val TABULAR_FIGURES = "tnum"

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
) {
    val colors = MaterialTheme.colorScheme
    val tertiary = ViolinTheme.textTertiary
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val interaction = remember { MutableInteractionSource() }
    // the disabled field keeps its colours: it is dimmed as a whole, as a disabled button is
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceContainer,
        unfocusedContainerColor = colors.surfaceContainer,
        disabledContainerColor = colors.surfaceContainer,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outlineVariant,
        disabledBorderColor = colors.outlineVariant,
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        disabledTextColor = colors.onSurface,
        disabledPlaceholderColor = tertiary,
        cursorColor = colors.primary,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)),
        enabled = enabled,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        decorationBox = { innerTextField ->
            Column(Modifier.fillMaxWidth()) {
                Text(label, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(LabelGap))
                // the frame is as wide as the field and 56 high at the least, as OutlinedTextField makes it
                Box(Modifier.fillMaxWidth().heightIn(min = OutlinedTextFieldDefaults.MinHeight), propagateMinConstraints = true) {
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = value.text,
                        innerTextField = innerTextField,
                        enabled = enabled,
                        singleLine = singleLine,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = interaction,
                        placeholder = placeholder?.let { { Text(it, color = tertiary, maxLines = if (singleLine) 1 else Int.MAX_VALUE) } },
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
                            Text(counter, color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = small.copy(fontFeatureSettings = TABULAR_FIGURES))
                        }
                    }
                }
            }
        },
    )
}
