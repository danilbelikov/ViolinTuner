package com.violinjourney.app.feature.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.ui.components.SystemScreens
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController

/** The system's sheet of «Поделиться»: the file, and the words beside it for the receivers that take them. */
@Composable
actual fun rememberFileSender(): (file: PlatformFile, text: String?) -> Unit = remember {
    { file, text ->
        val items = listOfNotNull<Any>(NSURL.fileURLWithPath(file.path), text)
        SystemScreens.present(UIActivityViewController(activityItems = items, applicationActivities = null))
    }
}
