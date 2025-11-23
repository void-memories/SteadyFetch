package dev.namn.steady_fetch.impl.notifications

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.namn.steady_fetch.R as SteadyFetchR
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import kotlin.math.roundToInt

internal class DownloadForegroundService : Service() {

    private val activeDownloads = LinkedHashMap<Long, DownloadEntry>()
    private var isInForeground = false

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START -> handleStart(intent)
            ACTION_UPDATE -> handleUpdate(intent)
            ACTION_CANCEL -> handleCancel(intent)
            else -> Unit
        }

        refreshNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleStart(intent: Intent) {
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: DEFAULT_FILE_NAME
        if (downloadId == -1L) return
        val existing = activeDownloads[downloadId]
        activeDownloads[downloadId] = DownloadEntry(
            id = downloadId,
            fileName = fileName,
            progress = existing?.progress ?: 0f,
            status = existing?.status ?: DownloadStatus.QUEUED
        )
    }

    private fun handleUpdate(intent: Intent) {
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
            ?: activeDownloads[downloadId]?.fileName
            ?: DEFAULT_FILE_NAME
        val progress = intent.getFloatExtra(EXTRA_PROGRESS, 0f).coerceIn(0f, 1f)
        val statusName = intent.getStringExtra(EXTRA_STATUS)
        val status = statusName?.let { runCatching { DownloadStatus.valueOf(it) }.getOrNull() }
            ?: DownloadStatus.RUNNING

        if (status == DownloadStatus.SUCCESS || status == DownloadStatus.FAILED) {
            activeDownloads.remove(downloadId)
        } else {
            activeDownloads[downloadId] = DownloadEntry(downloadId, fileName, progress, status)
        }
    }

    private fun handleCancel(intent: Intent) {
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return
        activeDownloads.remove(downloadId)
    }

    private fun refreshNotification() {
        if (activeDownloads.isEmpty()) {
            stopIfIdle()
            return
        }

        if (!canPostNotifications()) {
            Log.w(TAG, "POST_NOTIFICATIONS permission missing; foreground updates skipped")
            return
        }

        val entries = activeDownloads.values.toList()
        val (title, body) = buildNotificationCopy(entries)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        if (!isInForeground) {
            startForeground(AGGREGATE_NOTIFICATION_ID, notification)
            isInForeground = true
        } else {
            notifySafely(AGGREGATE_NOTIFICATION_ID, notification)
        }
    }

    private fun stopIfIdle() {
        if (!isInForeground) {
            stopSelf()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        isInForeground = false
        stopSelf()
    }

    private fun buildNotificationCopy(entries: List<DownloadEntry>): Pair<String, String> {
        if (entries.isEmpty()) return DEFAULT_FILE_NAME to ""
        if (entries.size == 1) {
            val name = entries.first().fileName
            return name to name
        }
        val title = getString(SteadyFetchR.string.steady_fetch_notification_multi, entries.size)
        val body = entries.joinToString(separator = "\n") { it.fileName }
        return title to body
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

    @SuppressLint("MissingPermission")
    private fun notifySafely(notificationId: Int, notification: Notification) {
        if (!canPostNotifications()) {
            Log.w(TAG, "POST_NOTIFICATIONS permission missing; skipping notify for $notificationId")
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Unable to post notification $notificationId", securityException)
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    companion object {
        private const val CHANNEL_ID = "steady_fetch_downloads"
        private const val AGGREGATE_NOTIFICATION_ID = 0x53_45_44 // "SED" hex
        private const val TAG = "SteadyFetchNotification"
        private const val DEFAULT_FILE_NAME = "SteadyFetch"

        const val ACTION_START = "dev.namn.steady_fetch.action.START"
        const val ACTION_UPDATE = "dev.namn.steady_fetch.action.UPDATE"
        const val ACTION_CANCEL = "dev.namn.steady_fetch.action.CANCEL"

        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"
    }

    private data class DownloadEntry(
        val id: Long,
        val fileName: String,
        val progress: Float,
        val status: DownloadStatus
    )
}
