package com.example.violintuner

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.backup.RestoreSwap
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ViolinTunerApp : Application() {
    /**
     * A copy that was unpacked by the process before this one takes the place of the data here —
     * before Hilt, before anything can have opened the database (spec 3.20, 5.14).
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        startedAfter = RestoreSwap.applyIfPending(base.filesDir, base.getDatabasePath(RestoreSwap.DATABASE_FILE).parentFile ?: base.filesDir)
    }

    override fun onCreate() {
        super.onCreate()
        Formats.use(resources.configuration.locales[0])
    }

    /** Numbers and dates speak the language the words were resolved in (the device's, or the one chosen for the app in the system settings). */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Formats.use(newConfig.locales[0])
    }

    companion object {
        /** What this process found to do at its start; the activity opens «Занятия» after a restore. */
        var startedAfter: RestoreSwap.Outcome = RestoreSwap.Outcome.NOTHING
            private set

        /** Read once: a rotation is not another restore. */
        fun consumeStartedAfter(): RestoreSwap.Outcome = startedAfter.also { startedAfter = RestoreSwap.Outcome.NOTHING }
    }
}
