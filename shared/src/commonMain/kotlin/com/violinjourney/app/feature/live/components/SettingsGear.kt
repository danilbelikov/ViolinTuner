package com.violinjourney.app.feature.live.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.violinjourney.app.core.ui.components.glass
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.nav_settings
import org.jetbrains.compose.resources.stringResource

/**
 * «Настройки» from Live (spec 3.8, 3.36.6): a gear on a disc of the glass of the switcher beside it, in the right corner of its
 * row — where every tuner keeps it, beside what it changes (A4, the tolerance). 40 dp to the eye, 48 to the finger (spec 5.29 R6).
 * While a recording runs it does not answer: leaving Live would end the take.
 */
@Composable
fun SettingsGear(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val label = stringResource(Res.string.nav_settings)
    val ground = liveGlassGround()
    Box(
        modifier = modifier
            .size(LiveDimens.GearTouch)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(LiveDimens.GearSize)
                .glass(CircleShape, edge = true, ground = ground),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Gear, contentDescription = null, size = LiveDimens.GearIcon, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
