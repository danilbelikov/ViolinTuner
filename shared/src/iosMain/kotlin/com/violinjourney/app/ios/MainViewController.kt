package com.violinjourney.app.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.window.ComposeUIViewController
import com.violinjourney.app.core.audio.FakeScenario
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

/**
 * The iOS app. `-fakeScenario IN_TUNE` plays a steady script instead of the microphone, as `-PfakeScenario` on Android:
 * `xcrun simctl launch booted com.violinjourney.app.debug -fakeScenario IN_TUNE`; `-openRoute live` opens a screen at once.
 */
@Suppress("FunctionName", "unused") // called from Swift
fun MainViewController(): UIViewController {
    useInterfaceLanguage()
    val graph = IosGraph(launchArgument<FakeScenario>("-fakeScenario"))
    val openRoute = launchText("-openRoute")
    return ComposeUIViewController {
        // the words of a scale are read once, before the first screen; it takes a moment of the dark surface
        val scaleTexts by produceState<IosScaleTexts?>(null) { value = IosScaleTexts.load() }
        scaleTexts?.let { IosApp(graph, it, openRoute) }
    }
}

private inline fun <reified T : Enum<T>> launchArgument(name: String): T? =
    launchText(name)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

/** The word after [name] among the arguments of the launch (`xcrun simctl launch … -openRoute live`). */
private fun launchText(name: String): String? {
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    val index = arguments.indexOf(name)
    return if (index < 0) null else arguments.getOrNull(index + 1)
}
