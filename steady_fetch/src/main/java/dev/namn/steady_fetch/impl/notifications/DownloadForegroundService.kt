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
    private var notificationManagerCompat: NotificationManagerCompat? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationManagerCompat = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        var shouldRefresh = false
        when (intent.action) {
            ACTION_START -> {
                handleStart(intent)
                shouldRefresh = true // Always refresh on start
            }
            ACTION_UPDATE -> {
                shouldRefresh = handleUpdate(intent) // Only refresh if status changed
            }
            ACTION_CANCEL -> {
                handleCancel(intent)
                shouldRefresh = true // Always refresh on cancel
            }
            else -> Unit
        }

        if (shouldRefresh) {
            refreshNotification()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleStart(intent: Intent) {
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: DEFAULT_FILE_NAME
        if (downloadId == -1L) return
        trimActiveDownloadsIfNeeded()
        val existing = activeDownloads[downloadId]
        activeDownloads[downloadId] = DownloadEntry(
            id = downloadId,
            fileName = truncateFileName(fileName),
            progress = existing?.progress ?: 0f,
            status = existing?.status ?: DownloadStatus.QUEUED
        )
    }

    private fun handleUpdate(intent: Intent): Boolean {
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return false
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
            ?: activeDownloads[downloadId]?.fileName
            ?: DEFAULT_FILE_NAME
        val progress = intent.getFloatExtra(EXTRA_PROGRESS, 0f).coerceIn(0f, 1f)
        val statusName = intent.getStringExtra(EXTRA_STATUS)
        val status = statusName?.let { runCatching { DownloadStatus.valueOf(it) }.getOrNull() }
            ?: DownloadStatus.RUNNING

        val previousStatus = activeDownloads[downloadId]?.status

        if (status == DownloadStatus.SUCCESS || status == DownloadStatus.FAILED) {
            // Download completed or failed - remove from active list
            activeDownloads.remove(downloadId)
            return true // Need to update notification
        } else {
            // Only update notification if status changed (e.g., QUEUED -> RUNNING)
            // Skip updates for pure progress changes (RUNNING -> RUNNING)
            val statusChanged = previousStatus != status
            if (statusChanged) {
                trimActiveDownloadsIfNeeded()
                activeDownloads[downloadId] = DownloadEntry(
                    downloadId,
                    truncateFileName(fileName),
                    progress,
                    status
                )
                return true // Status changed, need to update notification
            } else {
                // Just a progress update, no notification change needed
                // Still update the entry but don't refresh notification
                trimActiveDownloadsIfNeeded()
                activeDownloads[downloadId] = DownloadEntry(
                    downloadId,
                    truncateFileName(fileName),
                    progress,
                    status
                )
                return false // No notification update needed
            }
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

        try {
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
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError while refreshing notification; clearing old entries", oom)
            trimActiveDownloadsToMax()
            // Try once more with reduced entries
            try {
                if (activeDownloads.isNotEmpty() && canPostNotifications()) {
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
                    if (isInForeground) {
                        notifySafely(AGGREGATE_NOTIFICATION_ID, notification)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to refresh notification after OOM recovery", e)
            }
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
        val builder = StringBuilder()
        entries.take(MAX_DISPLAYED_DOWNLOADS).forEachIndexed { index, entry ->
            if (index > 0) builder.append('\n')
            val name = entry.fileName
            if (name.length > MAX_NAME_LENGTH) {
                builder.append(name.take(MAX_NAME_LENGTH)).append(ELLIPSIS)
            } else {
                builder.append(name)
            }
        }
        val remaining = entries.size - MAX_DISPLAYED_DOWNLOADS
        if (remaining > 0) {
            builder.append('\n')
            builder.append(getString(SteadyFetchR.string.steady_fetch_notification_more, remaining))
        }
        if (builder.length > MAX_BODY_CHARS) {
            return title to builder.substring(0, MAX_BODY_CHARS) + ELLIPSIS
        }
        return title to builder.toString()
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
            getNotificationManagerCompat()?.notify(notificationId, notification)
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Unable to post notification $notificationId", securityException)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError while posting notification", oom)
        }
    }

    private fun getNotificationManagerCompat(): NotificationManagerCompat? {
        return try {
            notificationManagerCompat ?: NotificationManagerCompat.from(this).also {
                notificationManagerCompat = it
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError creating NotificationManagerCompat", oom)
            null
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
        val manager = getNotificationManagerCompat() ?: return false
        return try {
            permissionGranted && manager.areNotificationsEnabled()
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError checking notification permissions", oom)
            false
        }
    }

    private fun truncateFileName(fileName: String): String {
        return if (fileName.length > MAX_STORED_FILENAME_LENGTH) {
            fileName.substring(0, MAX_STORED_FILENAME_LENGTH) + ELLIPSIS
        } else {
            fileName
        }
    }

    private fun trimActiveDownloadsIfNeeded() {
        if (activeDownloads.size >= MAX_ACTIVE_DOWNLOADS) {
            trimActiveDownloadsToMax()
        }
    }

    private fun trimActiveDownloadsToMax() {
        if (activeDownloads.size <= MAX_ACTIVE_DOWNLOADS) return
        val toRemove = activeDownloads.size - MAX_ACTIVE_DOWNLOADS
        val iterator = activeDownloads.iterator()
        var removed = 0
        while (iterator.hasNext() && removed < toRemove) {
            val entry = iterator.next()
            // Prefer removing completed/failed downloads
            if (entry.value.status == DownloadStatus.SUCCESS || entry.value.status == DownloadStatus.FAILED) {
                iterator.remove()
                removed++
            }
        }
        // If we still need to remove more, remove oldest entries
        while (iterator.hasNext() && removed < toRemove) {
            iterator.next()
            iterator.remove()
            removed++
        }
    }

    companion object {
        private const val CHANNEL_ID = "steady_fetch_downloads"
        private const val AGGREGATE_NOTIFICATION_ID = 0x53_45_44 // "SED" hex
        private const val TAG = "SteadyFetchNotification"
        private const val DEFAULT_FILE_NAME = "SteadyFetch"
        private const val MAX_DISPLAYED_DOWNLOADS = 10
        private const val MAX_NAME_LENGTH = 64
        private const val MAX_BODY_CHARS = 2048
        private const val MAX_STORED_FILENAME_LENGTH = 128
        private const val MAX_ACTIVE_DOWNLOADS = 50
        private const val ELLIPSIS = "…"

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
