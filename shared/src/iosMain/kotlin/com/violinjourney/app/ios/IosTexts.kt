package com.violinjourney.app.ios

import com.violinjourney.app.core.time.WallClock

/** The words the view models ask for without suspending, read once when the app starts (see [IosScaleTexts]). */
internal class IosTexts(val scale: IosScaleTexts, val share: IosShareTexts) {
    companion object {
        suspend fun load(clock: WallClock) = IosTexts(IosScaleTexts.load(), IosShareTexts.load(clock))
    }
}
