package dev.namn.steady_fetch.impl.notifications

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import org.junit.Assert.assertEquals
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
        val intent = baseIntent(DownloadForegroundService.ACTION_START)
        val result = service.onStartCommand(intent, 0, 0)
        assertEquals(android.app.Service.START_STICKY, result)
    }

    @Test
    fun onStartCommand_updateSuccess_stopsService() {
        val intent = baseIntent(DownloadForegroundService.ACTION_UPDATE).apply {
            putExtra(DownloadForegroundService.EXTRA_STATUS, DownloadStatus.SUCCESS.name)
        }

        service.onStartCommand(intent, 0, 0)

        val shadow = Shadows.shadowOf(service)
        assertTrue(shadow.isStoppedBySelf)
    }

    @Test
    fun onStartCommand_cancelAction_stopsService() {
        val intent = baseIntent(DownloadForegroundService.ACTION_CANCEL)
        service.onStartCommand(intent, 0, 0)
        val shadow = Shadows.shadowOf(service)
        assertTrue(shadow.isStoppedBySelf)
    }

    private fun baseIntent(action: String) = Intent(ApplicationProvider.getApplicationContext(), DownloadForegroundService::class.java).apply {
        this.action = action
        putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, 99L)
        putExtra(DownloadForegroundService.EXTRA_FILE_NAME, "demo.bin")
    }
}

