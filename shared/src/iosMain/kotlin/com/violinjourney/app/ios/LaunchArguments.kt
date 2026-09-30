package com.violinjourney.app.ios

import com.violinjourney.app.feature.live.LiveMode
import platform.Foundation.NSProcessInfo

/**
 * The arguments of the launch, for checks by screenshot: `xcrun simctl launch booted com.violinjourney.app.debug
 * -openRoute live -fakeScenario IN_TUNE -liveMode tuning -sceneSeconds 12.5 -noBake`. They never change in a process, so they
 * are read once, here, for every one who asks.
 */
internal object LaunchArguments {
    private val all: List<String> by lazy { NSProcessInfo.processInfo.arguments.map { it.toString() } }

    /**
     * The mode Live opens in, `-liveMode tuning` (spec 3.36.6): `simctl` cannot tap «Настройка», and a screenshot of it needs the
     * mode from the start. Only the owner's .debug app listens ([IosBuild.isDevApp]); null — as ever, «Игра».
     */
    val liveMode: LiveMode? by lazy { liveModeOf(all, IosBuild.isDevApp) }

    /** The word after [name]; null when there is none. */
    fun valueOf(name: String): String? = valueAfter(all, name)

    /** A switch without a value, such as `-noBake`. */
    fun has(name: String): Boolean = name in all

    /** The word after the first [name] among [arguments]. Pure. */
    fun valueAfter(arguments: List<String>, name: String): String? {
        val index = arguments.indexOf(name)
        return if (index < 0) null else arguments.getOrNull(index + 1)
    }

    /**
     * The mode after `-liveMode` among [arguments], whatever its case; null in an app not the owner's ([devApp] false), without the
     * argument and for a word that names no mode. Pure.
     */
    fun liveModeOf(arguments: List<String>, devApp: Boolean): LiveMode? {
        if (!devApp) return null
        val value = valueAfter(arguments, LIVE_MODE) ?: return null
        return LiveMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    private const val LIVE_MODE = "-liveMode"
}
