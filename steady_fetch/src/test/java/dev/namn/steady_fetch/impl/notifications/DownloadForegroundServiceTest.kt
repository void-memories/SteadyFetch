package dev.namn.steady_fetch.impl.notifications

import android.content.Intent
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class DownloadForegroundServiceTest {

    private lateinit var service: DownloadForegroundService

    @Before
    fun setUp() {
        service = Robolectric.buildService(DownloadForegroundService::class.java).create().get()
    }

    @Test
    fun onStartCommand_startAction_isSticky() {
        val intent = startIntent(42L, "demo.bin")
        val result = service.onStartCommand(intent, 0, 0)
        assertEquals(android.app.Service.START_STICKY, result)
    }

    @Test
    fun onStartCommand_updateSuccess_stopsServiceWhenLast() {
        service.onStartCommand(startIntent(1L, "file-a"), 0, 0)
        val intent = updateIntent(1L).apply {
            putExtra(DownloadForegroundService.EXTRA_STATUS, DownloadStatus.SUCCESS.name)
        }

        service.onStartCommand(intent, 0, 0)

        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun cancel_doesNotStopWhileOtherDownloadsRemain() {
        service.onStartCommand(startIntent(1L, "alpha"), 0, 0)
        service.onStartCommand(startIntent(2L, "beta"), 0, 0)

        service.onStartCommand(cancelIntent(1L), 0, 0)
        assertFalse(Shadows.shadowOf(service).isStoppedBySelf)

        service.onStartCommand(cancelIntent(2L), 0, 0)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun notification_listsActiveDownloadsWhenMultiple() {
        service.onStartCommand(startIntent(1L, "alpha"), 0, 0)
        service.onStartCommand(startIntent(2L, "beta"), 0, 0)
        service.onStartCommand(updateIntent(1L).apply {
            putExtra(DownloadForegroundService.EXTRA_PROGRESS, 0.5f)
        }, 0, 0)

        val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = Shadows.shadowOf(manager).allNotifications.lastOrNull()
        assertNotNull(notification)
        val extras = notification!!.extras
        val combinedText = buildString {
            append(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            append("\n")
            append(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty())
        }
        assertTrue("combinedText=$combinedText", combinedText.contains("alpha"))
        assertTrue("combinedText=$combinedText", combinedText.contains("beta"))
    }

    private fun startIntent(id: Long, name: String) =
        Intent(ApplicationProvider.getApplicationContext(), DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_START
            putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, id)
            putExtra(DownloadForegroundService.EXTRA_FILE_NAME, name)
        }

    private fun updateIntent(id: Long) =
        Intent(ApplicationProvider.getApplicationContext(), DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_UPDATE
            putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, id)
            putExtra(DownloadForegroundService.EXTRA_STATUS, DownloadStatus.RUNNING.name)
        }

    private fun cancelIntent(id: Long) =
        Intent(ApplicationProvider.getApplicationContext(), DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_CANCEL
            putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, id)
        }
}

