package dev.namn.steady_fetch.impl.notifications

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus

internal class DownloadNotificationManager(
    private val application: Application
) {

    fun start(downloadId: Long, fileName: String) {
        val intent = Intent(application, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_START
            putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, downloadId)
            putExtra(DownloadForegroundService.EXTRA_FILE_NAME, sanitize(fileName))
        }
        ContextCompat.startForegroundService(application, intent)
    }

    fun update(downloadId: Long, fileName: String, progress: Float, status: DownloadStatus) {
        val intent = Intent(application, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_UPDATE
            putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, downloadId)
            putExtra(DownloadForegroundService.EXTRA_FILE_NAME, sanitize(fileName))
            putExtra(DownloadForegroundService.EXTRA_PROGRESS, progress)
            putExtra(DownloadForegroundService.EXTRA_STATUS, status.name)
        }
        application.startService(intent)
    }

    fun cancel(downloadId: Long) {
        val intent = Intent(application, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_CANCEL
            putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        application.startService(intent)
    }

    private fun sanitize(name: String): String {
        if (name.length <= MAX_FILENAME_LENGTH) return name
        return name.substring(0, MAX_FILENAME_LENGTH) + INTENT_ELLIPSIS
    }

    companion object {
        private const val MAX_FILENAME_LENGTH = 512
        private const val INTENT_ELLIPSIS = "…"
    }
}
