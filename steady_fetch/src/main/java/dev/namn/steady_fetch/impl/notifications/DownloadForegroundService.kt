package dev.namn.steady_fetch.impl.notifications

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import kotlin.math.roundToInt

internal class DownloadForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "SteadyFetch"
        val progress = intent.getFloatExtra(EXTRA_PROGRESS, 0f).coerceIn(0f, 1f)
        val statusName = intent.getStringExtra(EXTRA_STATUS)
        val status = statusName?.let { runCatching { DownloadStatus.valueOf(it) }.getOrNull() }
            ?: DownloadStatus.RUNNING

        val notification = buildNotification(fileName, progress, status)
        val notificationId = notificationIdFor(downloadId)

        when (intent.action) {
            ACTION_START -> {
                startForeground(notificationId, notification)
            }
            ACTION_UPDATE -> {
                NotificationManagerCompat.from(this).notify(notificationId, notification)
                if (status == DownloadStatus.SUCCESS || status == DownloadStatus.FAILED) {
                    stopAndRemove(notificationId)
                }
            }
            ACTION_CANCEL -> {
                stopAndRemove(notificationId)
            }
            else -> {
                // Ignore unknown action
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(
        fileName: String,
        progress: Float,
        status: DownloadStatus
    ): Notification {
        val percent = (progress.coerceIn(0f, 1f) * 100f).roundToInt()
        val contentText = when (status) {
            DownloadStatus.RUNNING -> "Transferring packets… $percent%"
            DownloadStatus.QUEUED -> "Queued – awaiting dispatch"
            DownloadStatus.SUCCESS -> "Download complete"
            DownloadStatus.FAILED -> "Download failed"
        }
        val iconRes = when (status) {
            DownloadStatus.SUCCESS -> R.drawable.stat_sys_download_done
            DownloadStatus.FAILED -> R.drawable.stat_notify_error
            else -> R.drawable.stat_sys_download
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(fileName)
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setOngoing(status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED)
            .setProgress(
                /* max= */ 100,
                /* progress= */ if (status == DownloadStatus.RUNNING) percent else 0,
                /* indeterminate= */ status == DownloadStatus.QUEUED
            )
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "SteadyFetch Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for ongoing SteadyFetch downloads"
        }
        manager.createNotificationChannel(channel)
    }

    private fun stopAndRemove(notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        NotificationManagerCompat.from(this).cancel(notificationId)
        stopSelf()
    }

    private fun notificationIdFor(downloadId: Long): Int {
        if (downloadId == -1L) return DEFAULT_NOTIFICATION_ID
        return ((downloadId xor (downloadId ushr 32)) and 0x7FFFFFFF).toInt()
    }

    companion object {
        private const val CHANNEL_ID = "steady_fetch_downloads"
        private const val DEFAULT_NOTIFICATION_ID = 0x53_45_44 // "SED" hex

        const val ACTION_START = "dev.namn.steady_fetch.action.START"
        const val ACTION_UPDATE = "dev.namn.steady_fetch.action.UPDATE"
        const val ACTION_CANCEL = "dev.namn.steady_fetch.action.CANCEL"

        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"
    }
}
