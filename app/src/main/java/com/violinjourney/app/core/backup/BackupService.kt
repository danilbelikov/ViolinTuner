package com.violinjourney.app.core.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.violinjourney.app.MainActivity
import com.violinjourney.app.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while a copy is being written or brought back (spec 3.20): gigabytes
 * take minutes, and minutes are long enough for the system to take a backgrounded app away.
 * The work itself is [BackupManager]'s; this only stands in the foreground with the one
 * notification the app has — its progress — and leaves when the job does. The permission to post
 * notifications is never asked for: without it the system shows nothing, and the work goes on all the same.
 */
@AndroidEntryPoint
class BackupService : Service() {
    @Inject lateinit var manager: BackupManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // within five seconds of being started, or the system takes the app down
        foreground(notificationOf(manager.job.value) ?: placeholder())
        scope.launch {
            var lastShownAt = 0L
            manager.job.takeWhile { it is BackupJob.Saving || it is BackupJob.Restoring }.collect { job ->
                // a notification a second is what the system has patience for
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastShownAt >= NOTIFY_EVERY_MS) {
                    lastShownAt = now
                    notificationOf(job)?.let { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, it) }
                }
            }
            ServiceCompat.stopForeground(this@BackupService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun foreground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun placeholder(): Notification = builder(getString(R.string.backup_notification_channel), null, null).build()

    private fun notificationOf(job: BackupJob): Notification? = when (job) {
        is BackupJob.Saving -> {
            val percent = ((job.progress?.fraction ?: 0f) * PERCENT).toInt()
            val phase = if (job.verifying) getString(R.string.backup_phase_verifying_file) else job.progress?.let { phaseOf(it) }
            builder(getString(R.string.backup_notification_saving, percent), withRemaining(phase, job.remainingSec), percent).build()
        }
        is BackupJob.Restoring -> {
            val percent = ((job.progress?.fraction ?: 0f) * PERCENT).toInt()
            val phase = when (job.phase) {
                RestorePhase.VERIFYING -> getString(R.string.restore_phase_verifying)
                RestorePhase.EXTRACTING -> job.progress?.let { getString(R.string.restore_phase_extracting, partName(it.part), it.index) } ?: getString(R.string.restore_step_extracting)
                RestorePhase.FINISHING -> getString(R.string.restore_step_finishing)
            }
            builder(getString(R.string.backup_notification_restoring, percent), withRemaining(phase, job.remainingSec), percent, restoring = true).build()
        }
        else -> null
    }

    private fun phaseOf(progress: BackupProgress): String =
        if (progress.part == BackupPart.DATA) getString(R.string.backup_phase_data) else getString(R.string.backup_phase_files, partName(progress.part), progress.index, progress.count)

    private fun partName(part: BackupPart): String = getString(
        when (part) {
            BackupPart.DATA -> R.string.backup_part_data_short
            BackupPart.SHEETS -> R.string.backup_part_sheets
            BackupPart.AUDIO -> R.string.backup_part_audio_short
            BackupPart.VIDEO -> R.string.backup_part_video
        },
    )

    private fun withRemaining(phase: String?, remainingSec: Int?): String? {
        val remaining = remainingSec?.let {
            val words = if (it < SECONDS_PER_MINUTE) getString(R.string.backup_remaining_seconds) else getString(R.string.backup_remaining_minutes, (it + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE)
            getString(R.string.backup_remaining, words)
        }
        return listOfNotNull(phase, remaining).joinToString(getString(R.string.dot_separator)).takeIf { it.isNotEmpty() }
    }

    private fun builder(title: String, text: String?, percent: Int?, restoring: Boolean = false): NotificationCompat.Builder {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // low importance: no sound, no peeking — it is a progress bar, not news
            manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.backup_notification_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_BACKUP, if (restoring) MainActivity.OPEN_RESTORING else MainActivity.OPEN_SAVING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_backup)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .apply { if (percent != null) setProgress(PERCENT, percent, false) else setProgress(0, 0, true) }
    }

    /** Started from the manager when a job begins. A start refused by the system — the app is not in front — is not the end of the job: it only runs without the shelter. */
    class Starter @Inject constructor(@ApplicationContext private val context: Context) : BackupKeepAlive {
        override fun start() {
            try {
                ContextCompat.startForegroundService(context, Intent(context, BackupService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "the foreground service could not be started; the copy goes on without it", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "the foreground service is not allowed; the copy goes on without it", e)
            }
        }
    }

    private companion object {
        const val TAG = "BackupService"
        const val CHANNEL = "backup"
        const val NOTIFICATION_ID = 41
        const val NOTIFY_EVERY_MS = 1_000L
        const val PERCENT = 100
        const val SECONDS_PER_MINUTE = 60
    }
}
