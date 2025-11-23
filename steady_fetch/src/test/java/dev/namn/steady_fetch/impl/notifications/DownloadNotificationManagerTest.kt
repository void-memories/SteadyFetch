package dev.namn.steady_fetch.impl.notifications

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadNotificationManagerTest {

    private val application: Application = mockk(relaxed = true)
    private val manager = DownloadNotificationManager(application)
    private val startIntentSlot = slot<Intent>()
    private val serviceIntentSlot = slot<Intent>()

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.startForegroundService(application, capture(startIntentSlot)) } answers {
            ComponentName("dev.namn.steady_fetch", DownloadForegroundService::class.java.name)
        }
        every { application.startService(capture(serviceIntentSlot)) } answers {
            ComponentName("dev.namn.steady_fetch", DownloadForegroundService::class.java.name)
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ========== start Tests ==========

    @Test
    fun start_buildsStartIntent() {
        manager.start(42L, "file.bin")

        val intent = startIntentSlot.captured
        assertEquals(DownloadForegroundService.ACTION_START, intent.action)
        assertEquals(42L, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
        assertEquals("file.bin", intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun start_withZeroDownloadId_passesId() {
        manager.start(0L, "file.bin")

        val intent = startIntentSlot.captured
        assertEquals(0L, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
    }

    @Test
    fun start_withNegativeDownloadId_passesId() {
        manager.start(-1L, "file.bin")

        val intent = startIntentSlot.captured
        assertEquals(-1L, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
    }

    @Test
    fun start_withVeryLargeDownloadId_passesId() {
        manager.start(Long.MAX_VALUE, "file.bin")

        val intent = startIntentSlot.captured
        assertEquals(Long.MAX_VALUE, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
    }

    @Test
    fun start_truncatesLongFileNamesForIntent() {
        val longName = buildString {
            repeat(600) { append('a') }
        }
        manager.start(1L, longName)

        val intent = startIntentSlot.captured
        val stored = intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME)!!
        assertTrue("Stored name should be <= 513 (512 + ellipsis), was ${stored.length}", stored.length <= 513)
        assertTrue("Stored name should end with ellipsis", stored.endsWith("…"))
    }

    @Test
    fun start_withExactMaxLengthFileName_preservesFileName() {
        val exactLengthName = "a".repeat(512)
        manager.start(1L, exactLengthName)

        val intent = startIntentSlot.captured
        assertEquals(exactLengthName, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun start_withOneOverMaxLengthFileName_truncates() {
        val tooLongName = "a".repeat(513)
        manager.start(1L, tooLongName)

        val intent = startIntentSlot.captured
        val stored = intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME)!!
        assertEquals(513, stored.length) // 512 + ellipsis
        assertTrue(stored.endsWith("…"))
    }

    @Test
    fun start_withEmptyFileName_passesEmptyString() {
        manager.start(1L, "")

        val intent = startIntentSlot.captured
        assertEquals("", intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun start_withSpecialCharacters_preservesCharacters() {
        val specialName = "file-with-特殊字符-àáâ.txt"
        manager.start(1L, specialName)

        val intent = startIntentSlot.captured
        assertEquals(specialName, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun start_callsStartForegroundService() {
        manager.start(1L, "file.bin")

        verify { ContextCompat.startForegroundService(application, any()) }
    }

    @Test
    fun start_withMultipleCalls_createsSeparateIntents() {
        manager.start(1L, "file1.bin")
        manager.start(2L, "file2.bin")
        manager.start(3L, "file3.bin")

        verify(exactly = 3) { ContextCompat.startForegroundService(application, any()) }
    }

    // ========== update Tests ==========

    @Test
    fun update_includesProgressPayload() {
        manager.update(7L, "chunk", 0.5f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals(DownloadForegroundService.ACTION_UPDATE, intent.action)
        assertEquals(0.5f, intent.getFloatExtra(DownloadForegroundService.EXTRA_PROGRESS, -1f), 0.001f)
    }

    @Test
    fun update_includesStatusPayload() {
        manager.update(7L, "chunk", 0.5f, DownloadStatus.SUCCESS)

        val intent = serviceIntentSlot.captured
        assertEquals(DownloadStatus.SUCCESS.name, intent.getStringExtra(DownloadForegroundService.EXTRA_STATUS))
    }

    @Test
    fun update_withAllStatusTypes_passesCorrectStatus() {
        DownloadStatus.values().forEach { status ->
            manager.update(1L, "file.bin", 0.5f, status)

            val intent = serviceIntentSlot.captured
            assertEquals(status.name, intent.getStringExtra(DownloadForegroundService.EXTRA_STATUS))
        }
    }

    @Test
    fun update_withZeroProgress_passesZero() {
        manager.update(1L, "file.bin", 0f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals(0f, intent.getFloatExtra(DownloadForegroundService.EXTRA_PROGRESS, -1f), 0f)
    }

    @Test
    fun update_withOneProgress_passesOne() {
        manager.update(1L, "file.bin", 1f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals(1f, intent.getFloatExtra(DownloadForegroundService.EXTRA_PROGRESS, -1f), 0f)
    }

    @Test
    fun update_withNegativeProgress_passesNegative() {
        manager.update(1L, "file.bin", -0.5f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals(-0.5f, intent.getFloatExtra(DownloadForegroundService.EXTRA_PROGRESS, -1f), 0.001f)
    }

    @Test
    fun update_withProgressGreaterThanOne_passesValue() {
        manager.update(1L, "file.bin", 1.5f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals(1.5f, intent.getFloatExtra(DownloadForegroundService.EXTRA_PROGRESS, -1f), 0.001f)
    }

    @Test
    fun update_includesFileName() {
        manager.update(1L, "my-file.bin", 0.5f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals("my-file.bin", intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun update_truncatesLongFileNames() {
        val longName = "a".repeat(600)
        manager.update(1L, longName, 0.5f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        val stored = intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME)!!
        assertTrue(stored.length <= 513)
        assertTrue(stored.endsWith("…"))
    }

    @Test
    fun update_callsStartService() {
        manager.update(1L, "file.bin", 0.5f, DownloadStatus.RUNNING)

        verify { application.startService(any()) }
    }

    @Test
    fun update_withMultipleCalls_createsSeparateIntents() {
        manager.update(1L, "file1.bin", 0.1f, DownloadStatus.RUNNING)
        manager.update(2L, "file2.bin", 0.2f, DownloadStatus.RUNNING)
        manager.update(3L, "file3.bin", 0.3f, DownloadStatus.RUNNING)

        verify(exactly = 3) { application.startService(any()) }
    }

    @Test
    fun update_includesDownloadId() {
        manager.update(123L, "file.bin", 0.5f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals(123L, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
    }

    // ========== cancel Tests ==========

    @Test
    fun cancel_targetsMatchingDownload() {
        manager.cancel(10L)

        val intent = serviceIntentSlot.captured
        assertEquals(DownloadForegroundService.ACTION_CANCEL, intent.action)
        assertEquals(10L, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
    }

    @Test
    fun cancel_withZeroDownloadId_passesId() {
        manager.cancel(0L)

        val intent = serviceIntentSlot.captured
        assertEquals(0L, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
    }

    @Test
    fun cancel_withNegativeDownloadId_passesId() {
        manager.cancel(-1L)

        val intent = serviceIntentSlot.captured
        assertEquals(-1L, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
    }

    @Test
    fun cancel_withVeryLargeDownloadId_passesId() {
        manager.cancel(Long.MAX_VALUE)

        val intent = serviceIntentSlot.captured
        assertEquals(Long.MAX_VALUE, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
    }

    @Test
    fun cancel_callsStartService() {
        manager.cancel(1L)

        verify { application.startService(any()) }
    }

    @Test
    fun cancel_withMultipleCalls_createsSeparateIntents() {
        manager.cancel(1L)
        manager.cancel(2L)
        manager.cancel(3L)

        verify(exactly = 3) { application.startService(any()) }
    }

    @Test
    fun cancel_doesNotIncludeFileName() {
        manager.cancel(1L)

        val intent = serviceIntentSlot.captured
        assertFalse(intent.hasExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun cancel_doesNotIncludeProgress() {
        manager.cancel(1L)

        val intent = serviceIntentSlot.captured
        assertFalse(intent.hasExtra(DownloadForegroundService.EXTRA_PROGRESS))
    }

    @Test
    fun cancel_doesNotIncludeStatus() {
        manager.cancel(1L)

        val intent = serviceIntentSlot.captured
        assertFalse(intent.hasExtra(DownloadForegroundService.EXTRA_STATUS))
    }

    // ========== sanitize Tests (indirect) ==========

    @Test
    fun sanitize_preservesShortFileNames() {
        val shortName = "file.bin"
        manager.start(1L, shortName)

        val intent = startIntentSlot.captured
        assertEquals(shortName, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun sanitize_truncatesVeryLongFileNames() {
        val veryLongName = "a".repeat(1000)
        manager.start(1L, veryLongName)

        val intent = startIntentSlot.captured
        val stored = intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME)!!
        assertEquals(513, stored.length) // 512 + ellipsis
        assertTrue(stored.endsWith("…"))
    }

    @Test
    fun sanitize_appliesToStartAndUpdate() {
        val longName = "a".repeat(600)

        manager.start(1L, longName)
        val startIntent = startIntentSlot.captured
        assertTrue(startIntent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME)!!.endsWith("…"))

        manager.update(2L, longName, 0.5f, DownloadStatus.RUNNING)
        val updateIntent = serviceIntentSlot.captured
        assertTrue(updateIntent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME)!!.endsWith("…"))
    }

    // ========== Edge Cases ==========

    @Test
    fun start_withUnicodeCharacters_preservesCharacters() {
        val unicodeName = "файл-文件-파일.txt"
        manager.start(1L, unicodeName)

        val intent = startIntentSlot.captured
        assertEquals(unicodeName, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun update_withUnicodeCharacters_preservesCharacters() {
        val unicodeName = "файл-文件-파일.txt"
        manager.update(1L, unicodeName, 0.5f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals(unicodeName, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun start_withSpacesInFileName_preservesSpaces() {
        val nameWithSpaces = "my file name.txt"
        manager.start(1L, nameWithSpaces)

        val intent = startIntentSlot.captured
        assertEquals(nameWithSpaces, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun update_withSpacesInFileName_preservesSpaces() {
        val nameWithSpaces = "my file name.txt"
        manager.update(1L, nameWithSpaces, 0.5f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals(nameWithSpaces, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun start_withNewlinesInFileName_handlesCorrectly() {
        val nameWithNewline = "file\nname.txt"
        manager.start(1L, nameWithNewline)

        val intent = startIntentSlot.captured
        assertEquals(nameWithNewline, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun update_withNewlinesInFileName_handlesCorrectly() {
        val nameWithNewline = "file\nname.txt"
        manager.update(1L, nameWithNewline, 0.5f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals(nameWithNewline, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun start_withSpecialCharactersInFileName_preservesCharacters() {
        val specialName = "file-name_with.special@chars#123.bin"
        manager.start(1L, specialName)

        val intent = startIntentSlot.captured
        assertEquals(specialName, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun update_withSpecialCharacters_preservesCharacters() {
        val specialName = "file-name_with.special@chars#123.bin"
        manager.update(1L, specialName, 0.5f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals(specialName, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))
    }

    @Test
    fun update_withFloatPrecision_preservesPrecision() {
        manager.update(1L, "file.bin", 0.123456789f, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        assertEquals(0.123456789f, intent.getFloatExtra(DownloadForegroundService.EXTRA_PROGRESS, -1f), 0.0000001f)
    }

    @Test
    fun update_withNaNProgress_handlesCorrectly() {
        manager.update(1L, "file.bin", Float.NaN, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        val progress = intent.getFloatExtra(DownloadForegroundService.EXTRA_PROGRESS, -1f)
        assertTrue(progress.isNaN())
    }

    @Test
    fun update_withInfinityProgress_handlesCorrectly() {
        manager.update(1L, "file.bin", Float.POSITIVE_INFINITY, DownloadStatus.RUNNING)

        val intent = serviceIntentSlot.captured
        val progress = intent.getFloatExtra(DownloadForegroundService.EXTRA_PROGRESS, -1f)
        assertTrue(progress.isInfinite())
    }

    // ========== Comprehensive Integration Tests ==========

    @Test
    fun comprehensiveWorkflow_startUpdateCancel() {
        val downloadId = 123L
        val fileName = "comprehensive-test.bin"

        // Start download
        manager.start(downloadId, fileName)
        var intent = startIntentSlot.captured
        assertEquals(DownloadForegroundService.ACTION_START, intent.action)
        assertEquals(downloadId, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
        assertEquals(fileName, intent.getStringExtra(DownloadForegroundService.EXTRA_FILE_NAME))

        // Update progress
        manager.update(downloadId, fileName, 0.3f, DownloadStatus.RUNNING)
        intent = serviceIntentSlot.captured
        assertEquals(DownloadForegroundService.ACTION_UPDATE, intent.action)
        assertEquals(0.3f, intent.getFloatExtra(DownloadForegroundService.EXTRA_PROGRESS, -1f), 0.001f)
        assertEquals(DownloadStatus.RUNNING.name, intent.getStringExtra(DownloadForegroundService.EXTRA_STATUS))

        // Update to success
        manager.update(downloadId, fileName, 1f, DownloadStatus.SUCCESS)
        intent = serviceIntentSlot.captured
        assertEquals(DownloadStatus.SUCCESS.name, intent.getStringExtra(DownloadForegroundService.EXTRA_STATUS))
        assertEquals(1f, intent.getFloatExtra(DownloadForegroundService.EXTRA_PROGRESS, -1f), 0f)

        // Cancel (shouldn't happen after success, but test the method)
        manager.cancel(downloadId)
        intent = serviceIntentSlot.captured
        assertEquals(DownloadForegroundService.ACTION_CANCEL, intent.action)
        assertEquals(downloadId, intent.getLongExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, -1))
    }

    @Test
    fun multipleDownloads_separateIntents() {
        manager.start(1L, "file1.bin")
        manager.start(2L, "file2.bin")
        manager.start(3L, "file3.bin")

        manager.update(1L, "file1.bin", 0.1f, DownloadStatus.RUNNING)
        manager.update(2L, "file2.bin", 0.2f, DownloadStatus.RUNNING)
        manager.update(3L, "file3.bin", 0.3f, DownloadStatus.RUNNING)

        manager.cancel(1L)
        manager.cancel(2L)
        manager.cancel(3L)

        verify(exactly = 3) { ContextCompat.startForegroundService(application, any()) }
        verify(exactly = 6) { application.startService(any()) } // 3 updates + 3 cancels
    }
}
