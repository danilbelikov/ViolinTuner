package com.violinjourney.app.ios

import platform.Foundation.NSProcessInfo

/**
 * The arguments of the launch, for checks by screenshot: `xcrun simctl launch booted com.violinjourney.app.debug
 * -openRoute live -fakeScenario IN_TUNE -sceneSeconds 12.5 -noBake`. They never change in a process, so they are read
 * once, here, for every one who asks.
 */
internal object LaunchArguments {
    private val all: List<String> by lazy { NSProcessInfo.processInfo.arguments.map { it.toString() } }

    /** The word after [name]; null when there is none. */
    fun valueOf(name: String): String? = valueAfter(all, name)

    /** A switch without a value, such as `-noBake`. */
    fun has(name: String): Boolean = name in all

    /** The word after the first [name] among [arguments]. Pure. */
    fun valueAfter(arguments: List<String>, name: String): String? {
        val index = arguments.indexOf(name)
        return if (index < 0) null else arguments.getOrNull(index + 1)
    }
}
