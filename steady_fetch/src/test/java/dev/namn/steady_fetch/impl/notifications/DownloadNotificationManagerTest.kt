package dev.namn.steady_fetch.impl.notifications

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadNotificationManagerTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val manager = DownloadNotificationManager(application)
    private val intentSlot = slot<Intent>()

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.startForegroundService(application, capture(intentSlot)) } answers {
            ComponentName(application, DownloadForegroundService::class.java)
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun start_buildsStartIntent() {
        manager.start(42L, "file.bin")

        val intent = intentSlot.captured
        assertEquals(DownloadForegroundService.ACTION_START, intent.action)
        assertEquals(42L, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
        assertEquals("file.bin", intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun update_includesProgressPayload() {
        manager.update(7L, "chunk", 0.5f, dev.namn.steady_fetch.impl.datamodels.DownloadStatus.RUNNING)

        val intent = intentSlot.captured
        assertEquals(DownloadForegroundService.ACTION_UPDATE, intent.action)
        assertEquals(0.5f, intent.getFloatExtra(DownloadForegroundService.EXTRA_PROGRESS, -1f))
    }

    @Test
    fun cancel_targetsMatchingDownload() {
        manager.cancel(10L)

        val intent = intentSlot.captured
        assertEquals(DownloadForegroundService.ACTION_CANCEL, intent.action)
        assertEquals(10L, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
    }
}

