package com.violinjourney.app.ios

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.Foundation.NSLog
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

/**
 * The iOS app. `-fakeScenario IN_TUNE` plays a steady script instead of the microphone, as `-PfakeScenario` on Android:
 * `xcrun simctl launch booted com.violinjourney.app.debug -fakeScenario IN_TUNE`; `-openRoute live` opens a screen at once.
 * In the owner's .debug app ([IosBuild]) `-sceneSeconds 12.5` stops the living pictures at that second and `-noBake`
 * draws them without baking, as the debug switches of Android (docs/plan-performance.md).
 *
 * The graph lives in [AppGraph], one per process, not in the composition: a second controller shows the same graph
 * instead of opening the same files a second time.
 */
@Suppress("FunctionName", "unused") // called from Swift
fun MainViewController(analytics: AnalyticsService?): UIViewController {
    AppGraph.start(analytics)
    val openRoute = launchText("-openRoute")
    // the whole screen is not pushed up for a focused field: the insets of the keyboard do that where it is needed
    val controller = ComposeUIViewController(configure = { onFocusBehavior = OnFocusBehavior.DoNothing }) {
        // null for the moment of a restart: the dark surface, as at the start
        val life = AppGraph.life
        if (life != null) {
            key(life) {
                // the view models of one graph go with its screens, and only then may a restart let the graph go
                DisposableEffect(life) {
                    life.screensShown()
                    onDispose { life.screensGone() }
                }
                // the words the view models need are read once, before the first screen; it takes a moment of the dark surface
                val texts by produceState<IosTexts?>(null) { value = IosTexts.load(life.graph.clock) }
                CompositionLocalProvider(LocalViewModelStoreOwner provides life, LocalAppRestart provides AppGraph::restart) {
                    texts?.let { IosApp(life.graph, it, openRoute) }
                }
            }
        }
    }
    SystemScreens.host = controller
    return controller
}

/** One graph and the view models of its screens: they live and go together. Main thread only. */
private class GraphLife(val graph: IosGraph) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    /** Compositions showing the screens of this graph: one, unless a controller comes back or a second one is made. */
    private val showing = MutableStateFlow(0)

    fun screensShown() {
        showing.value++
    }

    /** The last composition of its screens is gone: their view models are cleared, and a restart waiting for it goes on. */
    fun screensGone() {
        showing.value--
        if (showing.value == 0) viewModelStore.clear()
    }

    suspend fun awaitScreensGone() {
        showing.first { it == 0 }
    }
}

/**
 * The graph of the app, one per process. A copy brought back (spec 3.20) is put in place before anything opens the data
 * — at the start, and at the «restart» after a restore, which lets the whole graph go, swaps the copy in and builds a new
 * graph, as Android does by starting its process anew. Main thread only.
 */
private object AppGraph {
    var life by mutableStateOf<GraphLife?>(null)
        private set

    /** Lives as long as the process: a restart must not be cancelled by the screens it takes down. */
    private val scope = MainScope()
    private var started = false
    private var fakeScenario: FakeScenario? = null
    private var statistics: IosAppMetricaAnalytics? = null
    private lateinit var data: PlatformFile

    /** The first call makes the graph; a later one — another controller — finds it made. */
    fun start(analytics: AnalyticsService?) {
        if (started) return
        started = true
        // before anything else: an unhandled Kotlin exception is kept on its way out and told at the next start (spec 3.34)
        val crashRecord = KotlinCrashes.defaultRecord()
        KotlinCrashes.install(crashRecord)
        useInterfaceLanguage()
        // the owner's .debug app (Debug and Profile, whatever its Kotlin binary): the switches of the living pictures listen
        SceneDebug.debugBuild = IosBuild.isDevApp
        // statistics are sent by a build that has a key (spec 5.27); the owner's .debug app only when asked to (`analyticsDebug=true`)
        val sends = IosBuild.sendsStatistics(IosSecrets.APPMETRICA_KEY, IosBuild.isDevApp, IosSecrets.ANALYTICS_IN_DEBUG)
        statistics = if (sends && analytics != null) IosAppMetricaAnalytics.activate(analytics, IosSecrets.APPMETRICA_KEY, logs = IosSecrets.ANALYTICS_IN_DEBUG) else null
        KotlinCrashes.takeKept(crashRecord)?.let { crash ->
            val told = statistics
            if (told != null) {
                told.crashed(crash)
            } else {
                NSLog("Kotlin crash of the last run: ${crash.type}: ${crash.message}".replace("%", "%%"))
            }
        }
        fakeScenario = launchArgument<FakeScenario>("-fakeScenario")
        data = PlatformFile(IosStorage.dataDirectory())
        IosRestoreSwap.applyIfPending(data)
        life = GraphLife(IosGraph(fakeScenario, statistics))
    }

    /**
     * In order, so that nothing of the old graph touches the files once the copy is in their place: its screens leave and
     * their view models are cleared, its settings are let go and waited for, its database closed — only then the swap and
     * the new graph. A second call while one is on its way does nothing.
     */
    fun restart() {
        val old = life ?: return
        life = null
        scope.launch {
            old.awaitScreensGone()
            old.graph.close()
            IosRestoreSwap.applyIfPending(data)
            life = GraphLife(IosGraph(fakeScenario, statistics))
        }
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
