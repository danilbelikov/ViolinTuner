package com.violinjourney.app.ios

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.violinjourney.app.core.analytics.AnalyticsService
import com.violinjourney.app.core.analytics.IosAppMetricaAnalytics
import com.violinjourney.app.core.analytics.KotlinCrashes
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.backup.IosRestoreSwap
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.ui.components.SystemScreens
import com.violinjourney.app.feature.backup.LocalAppRestart
import com.violinjourney.app.feature.journey.art.SceneDebug
import platform.Foundation.NSLog
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

/**
 * The iOS app. `-fakeScenario IN_TUNE` plays a steady script instead of the microphone, as `-PfakeScenario` on Android:
 * `xcrun simctl launch booted com.violinjourney.app.debug -fakeScenario IN_TUNE`; `-openRoute live` opens a screen at once.
 * In the owner's .debug app ([IosBuild]) `-sceneSeconds 12.5` stops the living pictures at that second and `-noBake`
 * draws them without baking, as the debug switches of Android (docs/plan-performance.md).
 *
 * A copy brought back (spec 3.20) is put in place before anything opens the data — here at the start, and at the
 * «restart» after a restore: the whole graph is let go, the copy swapped in, and a new graph built, as Android does by
 * starting its process anew.
 */
@Suppress("FunctionName", "unused") // called from Swift
fun MainViewController(analytics: AnalyticsService?): UIViewController {
    // before anything else: an unhandled Kotlin exception is kept on its way out and told at the next start (spec 3.34)
    val crashRecord = KotlinCrashes.defaultRecord()
    KotlinCrashes.install(crashRecord)
    useInterfaceLanguage()
    // the owner's .debug app (Debug and Profile, whatever its Kotlin binary): the switches of the living pictures listen
    SceneDebug.debugBuild = IosBuild.isDevApp
    // statistics are sent by a build that has a key (spec 5.27); the owner's .debug app only when asked to (`analyticsDebug=true`)
    val sends = IosBuild.sendsStatistics(IosSecrets.APPMETRICA_KEY, IosBuild.isDevApp, IosSecrets.ANALYTICS_IN_DEBUG)
    val statistics = if (sends && analytics != null) IosAppMetricaAnalytics.activate(analytics, IosSecrets.APPMETRICA_KEY, logs = IosSecrets.ANALYTICS_IN_DEBUG) else null
    KotlinCrashes.takeKept(crashRecord)?.let { crash ->
        if (statistics != null) {
            statistics.crashed(crash)
        } else {
            NSLog("Kotlin crash of the last run: ${crash.type}: ${crash.message}".replace("%", "%%"))
        }
    }
    val fakeScenario = launchArgument<FakeScenario>("-fakeScenario")
    val openRoute = launchText("-openRoute")
    val data = PlatformFile(IosStorage.dataDirectory())
    IosRestoreSwap.applyIfPending(data)
    // the whole screen is not pushed up for a focused field: the insets of the keyboard do that where it is needed
    val controller = ComposeUIViewController(configure = { onFocusBehavior = OnFocusBehavior.DoNothing }) {
        var graph by remember { mutableStateOf(IosGraph(fakeScenario, statistics)) }
        val restart = remember {
            {
                graph.close()
                IosRestoreSwap.applyIfPending(data)
                graph = IosGraph(fakeScenario, statistics)
            }
        }
        key(graph) {
            // the view models of one graph die with it: a new graph starts with none
            val owner = remember { GraphViewModels() }
            DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
            // the words the view models need are read once, before the first screen; it takes a moment of the dark surface
            val texts by produceState<IosTexts?>(null) { value = IosTexts.load(graph.clock) }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalAppRestart provides restart) {
                texts?.let { IosApp(graph, it, openRoute) }
            }
        }
    }
    SystemScreens.host = controller
    return controller
}

private class GraphViewModels : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

private inline fun <reified T : Enum<T>> launchArgument(name: String): T? =
    launchText(name)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

/** The word after [name] among the arguments of the launch (`xcrun simctl launch … -openRoute live`). */
private fun launchText(name: String): String? {
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    val index = arguments.indexOf(name)
    return if (index < 0) null else arguments.getOrNull(index + 1)
}
