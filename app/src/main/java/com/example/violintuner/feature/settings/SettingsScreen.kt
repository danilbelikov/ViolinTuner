package com.example.violintuner.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.TolerancePreset
import com.example.violintuner.core.domain.UserSettings
import com.example.violintuner.core.domain.sound.BuiltInPreset
import com.example.violintuner.core.ui.components.A4Selector
import com.example.violintuner.core.ui.components.TolerancePresetList
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.icons.IconLabel
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.sound.SoundCaption
import com.example.violintuner.feature.sound.captionName

private val ScreenPaddingHorizontal = 20.dp
private val TitlePaddingTop = 28.dp
private val SectionSpacing = 28.dp
private val ItemSpacing = 10.dp
private val MaxContentWidth = 480.dp
private val TitleFontSize = 32.sp

/** Minimal settings (spec 3.8): the two choices of the onboarding and a way to see it again. */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    /** The block «Данные» (spec 3.20): it has a view model of its own, the settings know nothing of copies. */
    dataBlock: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ScreenPaddingHorizontal,
                    end = ScreenPaddingHorizontal,
                    top = TitlePaddingTop,
                    bottom = SectionSpacing,
                ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Text(
                text = stringResource(R.string.nav_settings),
                color = colors.onSurface,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = TitleFontSize,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Section(
                icon = AppIcons.Fork,
                title = stringResource(R.string.settings_a4_title),
                text = stringResource(R.string.settings_a4_text),
            ) {
                A4Selector(
                    optionsHz = state.a4OptionsHz,
                    selectedHz = state.a4Hz,
                    onSelect = { onIntent(SettingsIntent.A4Selected(it)) },
                )
            }
            Section(
                icon = AppIcons.Target,
                title = stringResource(R.string.settings_tolerance_title),
                text = stringResource(R.string.settings_tolerance_text),
            ) {
                TolerancePresetList(
                    selected = state.tolerance,
                    onSelect = { onIntent(SettingsIntent.ToleranceSelected(it)) },
                )
            }
            // A way in, not a control: the default sound of all recordings has a screen of its own (spec 3.17).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(role = Role.Button) { onIntent(SettingsIntent.SoundClicked) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppIcon(AppIcons.Sound, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sound_settings_row), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.sound_settings_row_caption, captionName(state.sound)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                AppIcon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            dataBlock()
            OutlinedButton(onClick = { onIntent(SettingsIntent.RestartOnboardingClicked) }) {
                IconLabel(AppIcons.Repeat, stringResource(R.string.settings_restart_onboarding))
            }
        }
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, text: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(icon, contentDescription = null, tint = colors.onSurfaceVariant)
            Text(text = title, color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
        }
        Text(text = text, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

@Preview(widthDp = 412, heightDp = 812)
@Composable
private fun SettingsScreenPreview() {
    ViolinTheme {
        SettingsScreen(
            state = SettingsState(442, UserSettings.A4_OPTIONS_HZ, TolerancePreset.BEGINNER, SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL)),
            onIntent = {},
        )
    }
}
