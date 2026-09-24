package com.violinjourney.app.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.ComposeUIViewController
import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.ui.theme.ViolinBaseTheme
import com.violinjourney.app.feature.live.LiveIntent
import com.violinjourney.app.feature.live.LiveMode
import com.violinjourney.app.feature.live.LivePresenter
import com.violinjourney.app.feature.live.LiveScreenLayout
import com.violinjourney.app.feature.live.LiveSlots
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.manrope_variable
import org.jetbrains.compose.resources.Font
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

/** The iOS app: Live on the looping fake script, on its plain dark field (step 2 of the port; the microphone is next). */
@Suppress("FunctionName", "unused") // called from Swift
fun MainViewController(): UIViewController = ComposeUIViewController { IosLive() }

@Composable
private fun IosLive() {
    val scope = rememberCoroutineScope()
    val presenter = remember {
        val config = IntonationConfig()
        LivePresenter(FakePitchSource(launchArgument("-fakeScenario", FakeScenario.DEMO), config), config, scope).apply {
            onIntent(LiveIntent.SelectMode(launchArgument("-liveMode", LiveMode.PLAY)))
        }
    }
    // Collected while the screen is composed. The fake source costs nothing in the background; the microphone
    // (next step) will stop with the app's lifecycle, as on Android.
    val state by presenter.state.collectAsState()
    ViolinBaseTheme(fontFamily = manrope()) {
        // the field reaches under the notch and the home indicator; the screen itself keeps out of them
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            LiveScreenLayout(
                state = state,
                onIntent = presenter::onIntent,
                slots = LiveSlots(),
                modifier = Modifier.safeDrawingPadding(),
            )
        }
    }
}

/**
 * A screen that stands still, for checks by eye and by screenshot, as `-PfakeScenario` on Android:
 * `xcrun simctl launch booted com.violinjourney.app -fakeScenario IN_TUNE -liveMode TUNING`.
 */
private inline fun <reified T : Enum<T>> launchArgument(name: String, default: T): T {
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    val value = arguments.getOrNull(arguments.indexOf(name) + 1)?.takeIf { arguments.contains(name) }
    return enumValues<T>().firstOrNull { it.name == value } ?: default
}

/** Manrope from the variable TTF, one file for every weight — as the app does on Android from res/font. */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun manrope(): FontFamily {
    val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
    val fonts = weights.map { weight ->
        Font(
            resource = Res.font.manrope_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    }
    return remember(fonts) { FontFamily(fonts) }
}
