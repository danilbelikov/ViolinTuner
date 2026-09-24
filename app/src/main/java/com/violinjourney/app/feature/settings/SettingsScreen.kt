package com.violinjourney.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.ui.components.A4Selector
import com.violinjourney.app.core.ui.components.TolerancePresetList
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.format.LOCALE
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconLabel
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.sound.SoundCaption
import com.violinjourney.app.feature.sound.captionName

private val ScreenPaddingHorizontal = 20.dp
private val TitlePaddingTop = 28.dp
private val TitlePaddingUnderBar = 4.dp
private val BackBarHeight = 56.dp
private val BackTouch = 48.dp
private val SectionSpacing = 28.dp
private val ItemSpacing = 10.dp
private val MaxContentWidth = 480.dp
private val TitleFontSize = 32.sp

/**
 * Minimal settings (spec 3.8): the two choices of the onboarding and a way to see it again. Above the tabs, opened
 * by the gear of Live (spec 4, handoff nav_bar 35): the arrow at the top leads back; null — none (previews).
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** The block «Данные» (spec 3.20): it has a view model of its own, the settings know nothing of copies. */
    dataBlock: @Composable () -> Unit = {},
    /** Opens the system's choice of the language of this app; null where the system has none (before Android 13) — there the app follows the device. */
    onLanguageClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        if (onBack != null) BackBar(onBack)
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ScreenPaddingHorizontal,
                        end = ScreenPaddingHorizontal,
                        top = if (onBack != null) TitlePaddingUnderBar else TitlePaddingTop,
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
                if (onLanguageClick != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(role = Role.Button, onClick = onLanguageClick)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppIcon(AppIcons.Globe, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_language), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                            Text(
                                // the language names itself in itself: «Deutsch», «한국어» — whoever looks for theirs finds it
                                text = Formats.LOCALE.getDisplayLanguage(Formats.LOCALE).replaceFirstChar { it.titlecase(Formats.LOCALE) },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        AppIcon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                dataBlock()
                OutlinedButton(onClick = { onIntent(SettingsIntent.RestartOnboardingClicked) }) {
                    IconLabel(AppIcons.Repeat, stringResource(R.string.settings_restart_onboarding))
                }
            }
        }
    }
}

/** The arrow back of a screen above the tabs, as on «Копия данных» and «Звук». */
@Composable
private fun BackBar(onBack: () -> Unit) {
    val label = stringResource(R.string.session_back)
    Box(modifier = Modifier.fillMaxWidth().height(BackBarHeight).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            modifier = Modifier.size(BackTouch).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Back, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
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
            state = SettingsState(442, UserSettings.A4_OPTIONS_HZ, TolerancePreset.BEGINNER, SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), analyticsEnabled = true),
            onIntent = {},
            onBack = {},
        )
    }
}
