package com.violinjourney.app.core.analytics

import android.app.Application

/**
 * Everything the app tells about itself (spec 3.34): numbers and enumerations only — never a title,
 * a name, a note, a file name or a sound. The service behind this interface is known to this
 * package alone, so changing it costs one file.
 */
interface Analytics {
    /**
     * Called once from [Application.onCreate]. Nothing is sent until the stored consent allows it,
     * so this is safe to call before the consent has been read from the disk.
     */
    fun start(application: Application)

    fun track(event: AnalyticsEvent)

    /**
     * An error the app handled instead of crashing. These group into one line with a counter
     * instead of dissolving among the events.
     */
    fun error(group: ErrorGroup, message: String, cause: Throwable? = null)
}

/** The handled errors worth counting (spec 3.34); the key is what groups them in the console. */
enum class ErrorGroup(val key: String) {
    /** The microphone could not be opened or stopped giving anything (spec 3.4, MicUnavailable). */
    MIC("mic"),

    /** A copy could not be written or, worse, could not be restored (spec 3.20). */
    BACKUP("backup"),

    /** The audio or video pipeline gave up: a codec, a container, a file (spec 3.17, 3.19). */
    MEDIA("media"),
}
