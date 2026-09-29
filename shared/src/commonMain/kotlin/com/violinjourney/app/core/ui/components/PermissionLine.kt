package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.mic_permission_grant
import org.jetbrains.compose.resources.stringResource

private const val REASON_LINES = 2

/**
 * A missing permission outside Live (spec 3.36.1, 5.29): one line of the [reason] over a text button «Разрешить доступ» ([grant])
 * in the accent — not a filled button and not a dialog. It stands in the bottom zone right over the key it holds back; the key
 * sleeps under it at 0.38 — the caller puts `AppButton(enabled = false)` there with no reason of its own: this line is its reason.
 * The same for a take and for the own camera ([icon] Camera there). The reason may take two lines (de, fr) and is said aloud
 * when it appears.
 *
 * [overPicture] — over the preview of the own camera: on dense glass (0.82) with the words in onSurface, never grey on glass, the
 * fields of its plates (10 / 12, 5.29 R4) and the whole reason on as many lines as it takes — it is the main phrase of that screen,
 * and the side column lying is 210 wide (four lines in en, de, it); [shape] — the corner of that glass (12 over the shutter).
 *
 * Stateless: what «Разрешить доступ» does is the caller's — the system request, or the settings of the app once the system asks no
 * more (`rememberMicPermissionRequester(openSettingsWhenBlocked = true)`, the own camera by its sign of «never», R4). The system
 * camera of a video takes no microphone of the app: its button does not sleep. Put on the screens in R4.
 */
@Composable
fun PermissionLine(
    reason: String,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = AppIcons.Mic,
    grant: String = stringResource(Res.string.mic_permission_grant),
    overPicture: Boolean = false,
    shape: Shape = AppShapes.M,
) {
    if (overPicture) {
        GlassPlate(
            modifier = modifier,
            strong = true,
            shape = shape,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Line(reason, MaterialTheme.colorScheme.onSurface, grant, icon, onGrant, maxLines = Int.MAX_VALUE)
            }
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Line(reason, MaterialTheme.colorScheme.onSurfaceVariant, grant, icon, onGrant, maxLines = REASON_LINES)
        }
    }
}

@Composable
private fun Line(reason: String, color: Color, grant: String, icon: ImageVector, onGrant: () -> Unit, maxLines: Int) {
    Text(
        text = reason,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = color,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    )
    AppButton(text = grant, onClick = onGrant, style = AppButtonStyle.Text, icon = icon)
}
