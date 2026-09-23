package com.violinjourney.app.feature.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme

private const val RIM_ALPHA = 0.22f

/**
 * «Настройки» from Live (spec 3.8, 4; handoff nav_bar 35): a gear on a disc of smoked glass in the right corner of
 * the switcher's row — where every tuner keeps it, beside what it changes (A4, the tolerance). 36 dp to the eye,
 * 48 to the finger. While a recording runs it does not answer: leaving Live would end the take.
 */
@Composable
fun SettingsGear(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ink = MaterialTheme.colorScheme.onSurface
    val label = stringResource(R.string.nav_settings)
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
                .background(ViolinTheme.venueColors.plate, CircleShape)
                .border(LiveDimens.GearBorder, ink.copy(alpha = RIM_ALPHA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Gear, contentDescription = null, size = LiveDimens.GearIcon, tint = ink)
        }
    }
}
