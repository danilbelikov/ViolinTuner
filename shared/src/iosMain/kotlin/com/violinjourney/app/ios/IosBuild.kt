package com.violinjourney.app.ios

import platform.Foundation.NSBundle

/**
 * Whether this is the owner's development app — the counterpart of Android's debug build type; on iOS the bundle id
 * carries it, because the Profile configuration runs a release Kotlin binary inside the owner's .debug app. In
 * iosApp.xcodeproj Debug and Profile are `com.violinjourney.app.debug` (the same app and data, Debug with a debug Kotlin
 * framework, Profile with a release one), Release is the store's `com.violinjourney.app`; `XcodeProjectTest` keeps it
 * so. Deliberately not `Platform.isDebugBinary`, which a Profile build would turn false.
 *
 * What follows it: statistics stay silent unless asked for ([sendsStatistics], spec 5.27), the microphone writes a line
 * of what the detector saw each second (FrameStats), and the living pictures listen to `-sceneSeconds` and `-noBake`
 * (`SceneDebug`).
 */
internal object IosBuild {
    /** The tail of the owner's bundle id, as `applicationIdSuffix` of the Android debug build. */
    const val DEV_BUNDLE_SUFFIX = ".debug"

    val isDevApp: Boolean = isDevBundle(NSBundle.mainBundle.bundleIdentifier)

    fun isDevBundle(bundleId: String?): Boolean = bundleId?.endsWith(DEV_BUNDLE_SUFFIX) == true

    /** A build with a key sends (spec 5.27); the owner's app only when asked to with `analyticsDebug=true`. */
    fun sendsStatistics(key: String, devApp: Boolean, analyticsInDebug: Boolean): Boolean = key.isNotBlank() && (!devApp || analyticsInDebug)
}
