package com.example.violintuner

import android.app.Application
import android.content.Context
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

    companion object {
        /** What this process found to do at its start; the activity opens «Занятия» after a restore. */
        var startedAfter: RestoreSwap.Outcome = RestoreSwap.Outcome.NOTHING
            private set

        /** Read once: a rotation is not another restore. */
        fun consumeStartedAfter(): RestoreSwap.Outcome = startedAfter.also { startedAfter = RestoreSwap.Outcome.NOTHING }
    }
}
