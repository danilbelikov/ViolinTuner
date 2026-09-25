package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/** A short word that goes away by itself («Слишком короткое занятие»): a toast on Android, the app's own on iOS. */
fun interface Messages {
    fun show(text: String)

    /** A word that needs a moment more to be read: a long toast on Android. */
    fun showLong(text: String) = show(text)
}

/** Where the screens put their short words; the root of each platform provides it. */
val LocalMessages = staticCompositionLocalOf { Messages {} }
