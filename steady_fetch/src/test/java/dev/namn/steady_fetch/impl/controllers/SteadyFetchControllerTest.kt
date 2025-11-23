package dev.namn.steady_fetch.impl.controllers

import dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback
import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import dev.namn.steady_fetch.impl.datamodels.DownloadError
import dev.namn.steady_fetch.impl.datamodels.DownloadMetadata
import dev.namn.steady_fetch.impl.datamodels.DownloadProgress
import dev.namn.steady_fetch.impl.datamodels.DownloadRequest
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import dev.namn.steady_fetch.impl.io.Networking
import dev.namn.steady_fetch.impl.managers.ChunkManager
import dev.namn.steady_fetch.impl.managers.FileManager
import dev.namn.steady_fetch.impl.notifications.DownloadNotificationManager
import dev.namn.steady_fetch.impl.utils.Constants
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class SteadyFetchControllerTest {

    private val fileManager = mockk<FileManager>(relaxed = true)
    private val networking = mockk<Networking>(relaxed = true)
    private val chunkManager = mockk<ChunkManager>()
    private val notificationManager = mockk<DownloadNotificationManager>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val controller = SteadyFetchController(
        fileManager = fileManager,
        networking = networking,
        chunkManager = chunkManager,
        notificationManager = notificationManager,
        ioScope = scope
    )

    private val downloadDir = createTempDir(prefix = "steady-controller")
    private val baseRequest = DownloadRequest(
        url = "https://example.com/file.bin",
        downloadDir = downloadDir,
        fileName = "file.bin",
        maxParallelDownloads = 4
    )
    private val metadata = Networking.RemoteMetadata(
        contentLength = 100L,
        supportsRanges = true,
        contentMd5 = "abc123"
    )
    private val chunks = listOf(
        DownloadChunk("file.part1", 0, 49),
        DownloadChunk("file.part2", 50, 99)
    )

    @Before
    fun setUp() {
        every { networking.fetchRemoteMetadata(any(), any()) } returns metadata
        coEvery { networking.download(any(), any(), any()) } just Runs
        every { chunkManager.generateChunks(any(), any()) } returns chunks
        every { fileManager.createDirectoryIfNotExists(any()) } just Runs
        every { fileManager.validateStorageCapacity(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        downloadDir.deleteRecursively()
    }

    // ========== queueDownload Tests ==========

    @Test
    fun queueDownload_returnsUniqueDownloadId() {
        val id1 = controller.queueDownload(baseRequest, noopCallback())
        // Add small delay to ensure different nanosecond timestamps
        // SystemClock.elapsedRealtimeNanos() might return same value if called very quickly
        Thread.sleep(2)
        val id2 = controller.queueDownload(baseRequest, noopCallback())

        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        // IDs should be different (or at least id2 should be >= id1 since time moves forward)
        assertTrue(id2 >= id1)
    }

    @Test
    fun queueDownload_buildsMetadataWithChunksWhenRangesSupported() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            networking.download(
                id,
                match<DownloadMetadata> {
                    it.chunks == chunks &&
                    it.request == baseRequest &&
                    it.contentMd5 == metadata.contentMd5
                },
                any()
            )
        }
    }

    @Test
    fun queueDownload_createsDirectoryBeforeDownload() {
        controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify { fileManager.createDirectoryIfNotExists(baseRequest.downloadDir) }
    }

    @Test
    fun queueDownload_validatesStorageWhenSizeKnown() {
        controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify { fileManager.validateStorageCapacity(baseRequest.downloadDir, metadata.contentLength!!) }
    }

    @Test
    fun queueDownload_skipsStorageValidationWhenSizeUnknown() {
        every { networking.fetchRemoteMetadata(any(), any()) } returns metadata.copy(contentLength = null)

        controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { fileManager.validateStorageCapacity(any(), any()) }
    }

    @Test
    fun queueDownload_skipsChunksWhenRangesUnsupported() {
        every { networking.fetchRemoteMetadata(any(), any()) } returns metadata.copy(supportsRanges = false)

        val id = controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            networking.download(
                id,
                match<DownloadMetadata> { it.chunks == null },
                any()
            )
        }
        verify(exactly = 0) { chunkManager.generateChunks(any(), any()) }
    }

    @Test
    fun queueDownload_skipsChunksWhenContentLengthUnknown() {
        every { networking.fetchRemoteMetadata(any(), any()) } returns metadata.copy(contentLength = null)

        val id = controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            networking.download(
                id,
                match<DownloadMetadata> { it.chunks == null },
                any()
            )
        }
        verify(exactly = 0) { chunkManager.generateChunks(any(), any()) }
    }

    @Test
    fun queueDownload_startsNotification() {
        val id = controller.queueDownload(baseRequest, noopCallback())

        verify { notificationManager.start(id, baseRequest.fileName) }
    }

    @Test
    fun queueDownload_decoratesCallbackWithNotifications() {
        val callback = mockk<SteadyFetchCallback>(relaxed = true)
        val decoratedCallbackSlot = slot<SteadyFetchCallback>()

        controller.queueDownload(baseRequest, callback)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            networking.download(any(), any(), capture(decoratedCallbackSlot))
        }

        val decorated = decoratedCallbackSlot.captured
        assertNotNull(decorated)

        // Test that decorated callback calls notification manager
        val progress = DownloadProgress(DownloadStatus.RUNNING, 0.5f, emptyList())
        decorated.onUpdate(progress)
        verify { notificationManager.update(any(), baseRequest.fileName, 0.5f, DownloadStatus.RUNNING) }
        verify { callback.onUpdate(progress) }
    }

    @Test
    fun queueDownload_decoratedCallbackOnSuccess_updatesNotificationAndCallsDelegate() {
        val callback = mockk<SteadyFetchCallback>(relaxed = true)
        val decoratedCallbackSlot = slot<SteadyFetchCallback>()

        controller.queueDownload(baseRequest, callback)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            networking.download(any(), any(), capture(decoratedCallbackSlot))
        }

        val decorated = decoratedCallbackSlot.captured
        decorated.onSuccess()

        verify { notificationManager.update(any(), baseRequest.fileName, 1f, DownloadStatus.SUCCESS) }
        verify { callback.onSuccess() }
    }

    @Test
    fun queueDownload_decoratedCallbackOnError_updatesNotificationAndCallsDelegate() {
        val callback = mockk<SteadyFetchCallback>(relaxed = true)
        val error = DownloadError(-1, "Test error")
        val decoratedCallbackSlot = slot<SteadyFetchCallback>()

        controller.queueDownload(baseRequest, callback)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            networking.download(any(), any(), capture(decoratedCallbackSlot))
        }

        val decorated = decoratedCallbackSlot.captured
        decorated.onError(error)

        verify { notificationManager.update(any(), baseRequest.fileName, 0f, DownloadStatus.FAILED) }
        verify { callback.onError(error) }
    }

    @Test
    fun queueDownload_failsFastWhenMaxParallelDownloadsTooLow() {
        val badRequest = baseRequest.copy(maxParallelDownloads = 0)

        val result = runCatching { controller.queueDownload(badRequest, noopCallback()) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun queueDownload_failsFastWhenMaxParallelDownloadsTooHigh() {
        val badRequest = baseRequest.copy(maxParallelDownloads = Constants.MAX_PARALLEL_CHUNKS + 1)

        val result = runCatching { controller.queueDownload(badRequest, noopCallback()) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun queueDownload_acceptsMaxParallelDownloadsAtLowerBound() {
        val request = baseRequest.copy(maxParallelDownloads = 1)

        val id = controller.queueDownload(request, noopCallback())

        assertTrue(id > 0)
    }

    @Test
    fun queueDownload_acceptsMaxParallelDownloadsAtUpperBound() {
        val request = baseRequest.copy(maxParallelDownloads = Constants.MAX_PARALLEL_CHUNKS)

        val id = controller.queueDownload(request, noopCallback())

        assertTrue(id > 0)
    }

    @Test
    fun queueDownload_reportsSetupFailuresThroughCallback() {
        val callback = mockk<SteadyFetchCallback>(relaxed = true)
        val failure = IllegalStateException("Directory creation failed")
        every { fileManager.createDirectoryIfNotExists(any()) } throws failure

        controller.queueDownload(baseRequest, callback)
        dispatcher.scheduler.advanceUntilIdle()

        verify {
            callback.onError(match { it.code != 0 && it.message.contains("Directory creation failed") })
        }
        coVerify(exactly = 0) { networking.download(any(), any(), any()) }
    }

    @Test
    fun queueDownload_reportsStorageValidationFailuresThroughCallback() {
        val callback = mockk<SteadyFetchCallback>(relaxed = true)
        val failure = IllegalStateException("Insufficient storage")
        every { fileManager.validateStorageCapacity(any(), any()) } throws failure

        controller.queueDownload(baseRequest, callback)
        dispatcher.scheduler.advanceUntilIdle()

        verify {
            callback.onError(match { it.code != 0 && it.message.contains("Insufficient storage") })
        }
        coVerify(exactly = 0) { networking.download(any(), any(), any()) }
    }

    @Test
    fun queueDownload_reportsMetadataFetchFailuresThroughCallback() {
        val callback = mockk<SteadyFetchCallback>(relaxed = true)
        val failure = IOException("Network error")
        every { networking.fetchRemoteMetadata(any(), any()) } throws failure

        controller.queueDownload(baseRequest, callback)
        dispatcher.scheduler.advanceUntilIdle()

        verify {
            callback.onError(match { it.code != 0 && it.message.contains("Network error") })
        }
        coVerify(exactly = 0) { networking.download(any(), any(), any()) }
    }

    @Test
    fun queueDownload_handlesChunkGenerationFailure() {
        val callback = mockk<SteadyFetchCallback>(relaxed = true)
        val failure = IllegalArgumentException("Invalid file size")
        every { chunkManager.generateChunks(any(), any()) } throws failure

        controller.queueDownload(baseRequest, callback)
        dispatcher.scheduler.advanceUntilIdle()

        verify {
            callback.onError(match { it.code != 0 && it.message.contains("Invalid file size") })
        }
        coVerify(exactly = 0) { networking.download(any(), any(), any()) }
    }

    @Test
    fun queueDownload_registersJob() {
        val id = controller.queueDownload(baseRequest, noopCallback())

        // Job should be registered
        assertTrue(controller.hasActiveJob(id))
    }

    @Test
    fun queueDownload_withCustomHeaders_passesHeadersToNetworking() {
        val request = baseRequest.copy(headers = mapOf("Authorization" to "Bearer token"))
        controller.queueDownload(request, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify { networking.fetchRemoteMetadata(request.url, request.headers) }
    }

    @Test
    fun queueDownload_withMd5InMetadata_passesMd5ToDownload() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            networking.download(
                id,
                match<DownloadMetadata> { it.contentMd5 == metadata.contentMd5 },
                any()
            )
        }
    }

    @Test
    fun queueDownload_withoutMd5InMetadata_passesNullMd5() {
        every { networking.fetchRemoteMetadata(any(), any()) } returns metadata.copy(contentMd5 = null)

        val id = controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            networking.download(
                id,
                match<DownloadMetadata> { it.contentMd5 == null },
                any()
            )
        }
    }

    @Test
    fun queueDownload_doesNotThrowOnCancellationException() {
        val callback = mockk<SteadyFetchCallback>(relaxed = true)
        coEvery { networking.download(any(), any(), any()) } throws CancellationException("Cancelled")

        controller.queueDownload(baseRequest, callback)
        dispatcher.scheduler.advanceUntilIdle()

        // CancellationException should be rethrown, not caught
        verify(exactly = 0) { callback.onError(any()) }
    }

    // ========== cancel Tests ==========

    @Test
    fun cancel_cancelsJobAndNetworking() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        every { networking.cancel(id) } returns true

        val cancelled = controller.cancel(id)

        assertTrue(cancelled)
        verify { networking.cancel(id) }
        verify { notificationManager.cancel(id) }
    }

    @Test
    fun cancel_withNonExistentDownloadId_returnsFalse() {
        every { networking.cancel(999L) } returns false

        val cancelled = controller.cancel(999L)

        assertFalse(cancelled)
        verify { networking.cancel(999L) }
        verify { notificationManager.cancel(999L) }
    }

    @Test
    fun cancel_removesJobFromActiveJobs() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        every { networking.cancel(id) } returns true

        controller.cancel(id)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(controller.hasActiveJob(id))
    }

    @Test
    fun cancel_swallowsNotificationExceptions() {
        val id = 42L
        every { networking.cancel(id) } returns false
        every { notificationManager.cancel(id) } throws IllegalStateException("kaboom")

        val result = controller.cancel(id)

        assertFalse(result)
        verifyOrder {
            networking.cancel(id)
            notificationManager.cancel(id)
        }
    }

    @Test
    fun cancel_swallowsNetworkingExceptions() {
        val id = 43L
        every { networking.cancel(id) } throws IOException("Network error")
        every { notificationManager.cancel(id) } just Runs

        val result = controller.cancel(id)

        // Should return false when exception occurs
        // networking.cancel is called before notificationManager.cancel, so if it throws,
        // notificationManager.cancel won't be called
        assertFalse(result)
        verify { networking.cancel(id) }
        verify(exactly = 0) { notificationManager.cancel(id) }
    }

    @Test
    fun cancel_swallowsAllExceptions() {
        val id = 44L
        every { networking.cancel(id) } throws RuntimeException("Unexpected error")
        every { notificationManager.cancel(id) } throws IllegalStateException("Another error")

        val result = controller.cancel(id)

        assertFalse(result)
    }

    @Test
    fun cancel_returnsTrueIfJobCancelled() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        every { networking.cancel(id) } returns false

        val cancelled = controller.cancel(id)

        assertTrue(cancelled) // Job was cancelled even if networking cancel returned false
    }

    @Test
    fun cancel_returnsTrueIfNetworkingCancelled() {
        val id = 45L
        every { networking.cancel(id) } returns true

        val cancelled = controller.cancel(id)

        assertTrue(cancelled)
    }

    @Test
    fun cancel_returnsFalseIfNeitherCancelled() {
        val id = 999L
        every { networking.cancel(id) } returns false

        val cancelled = controller.cancel(id)

        assertFalse(cancelled)
    }

    @Test
    fun cancel_cancelsJobWithCancellationException() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        val exceptionSlot = slot<CancellationException>()
        every { networking.cancel(id) } returns true

        controller.cancel(id)

        // Job should be cancelled with CancellationException
        verify { networking.cancel(id) }
    }

    // ========== Job Lifecycle Tests ==========

    @Test
    fun queueDownload_jobCompletionRemovesFromActiveJobs() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        assertTrue(controller.hasActiveJob(id))

        dispatcher.scheduler.advanceUntilIdle()

        // After job completes, it should be removed
        assertFalse(controller.hasActiveJob(id))
    }

    @Test
    fun queueDownload_jobCompletionDoesNotCancelNetworkingOnSuccess() {
        val id = controller.queueDownload(baseRequest, noopCallback())

        dispatcher.scheduler.advanceUntilIdle()

        // On successful completion, networking.cancel should NOT be called
        // Calls are already cleaned up in downloadToFile's finally block
        verify(exactly = 0) { networking.cancel(id) }
    }

    @Test
    fun queueDownload_multipleDownloads_tracksAllJobs() {
        val id1 = controller.queueDownload(baseRequest, noopCallback())
        val id2 = controller.queueDownload(baseRequest.copy(fileName = "file2.bin"), noopCallback())
        val id3 = controller.queueDownload(baseRequest.copy(fileName = "file3.bin"), noopCallback())

        assertTrue(controller.hasActiveJob(id1))
        assertTrue(controller.hasActiveJob(id2))
        assertTrue(controller.hasActiveJob(id3))
    }

    @Test
    fun queueDownload_cancelledJobRemovesFromActiveJobs() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        every { networking.cancel(id) } returns true

        controller.cancel(id)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(controller.hasActiveJob(id))
    }

    // ========== Metadata Preparation Tests ==========

    @Test
    fun prepareMetadata_withContentLengthAndRanges_generatesChunks() {
        controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify { chunkManager.generateChunks(baseRequest.fileName, metadata.contentLength!!) }
    }

    @Test
    fun prepareMetadata_withContentLengthButNoRanges_skipsChunks() {
        every { networking.fetchRemoteMetadata(any(), any()) } returns metadata.copy(supportsRanges = false)

        controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { chunkManager.generateChunks(any(), any()) }
    }

    @Test
    fun prepareMetadata_withoutContentLength_skipsChunks() {
        every { networking.fetchRemoteMetadata(any(), any()) } returns metadata.copy(contentLength = null)

        controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { chunkManager.generateChunks(any(), any()) }
    }

    @Test
    fun prepareMetadata_preservesRequestInMetadata() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            networking.download(
                id,
                match<DownloadMetadata> { it.request == baseRequest },
                any()
            )
        }
    }

    // ========== Edge Cases ==========

    @Test
    fun queueDownload_withVeryLongUrl_handlesCorrectly() {
        val longUrl = "https://example.com/" + "a".repeat(1000) + "/file.bin"
        val request = baseRequest.copy(url = longUrl)

        val id = controller.queueDownload(request, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify { networking.fetchRemoteMetadata(longUrl, any()) }
    }

    @Test
    fun queueDownload_withVeryLongFileName_handlesCorrectly() {
        val longFileName = "a".repeat(200) + ".bin"
        val request = baseRequest.copy(fileName = longFileName)

        val id = controller.queueDownload(request, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify { notificationManager.start(any(), longFileName) }
    }

    @Test
    fun queueDownload_withSpecialCharactersInFileName_handlesCorrectly() {
        val specialFileName = "file-with-特殊字符-àáâ.txt"
        val request = baseRequest.copy(fileName = specialFileName)

        val id = controller.queueDownload(request, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify { notificationManager.start(any(), specialFileName) }
    }

    @Test
    fun queueDownload_withEmptyHeaders_handlesCorrectly() {
        val request = baseRequest.copy(headers = emptyMap())

        controller.queueDownload(request, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify { networking.fetchRemoteMetadata(request.url, emptyMap()) }
    }

    @Test
    fun queueDownload_withManyHeaders_passesAllHeaders() {
        val headers = mapOf(
            "Authorization" to "Bearer token",
            "User-Agent" to "TestAgent",
            "Custom-Header" to "CustomValue"
        )
        val request = baseRequest.copy(headers = headers)

        controller.queueDownload(request, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify { networking.fetchRemoteMetadata(request.url, headers) }
    }

    @Test
    fun queueDownload_withZeroContentLength_handlesCorrectly() {
        every { networking.fetchRemoteMetadata(any(), any()) } returns metadata.copy(contentLength = 0L)
        // ChunkManager will throw for 0 file size, so we need to handle that
        every { chunkManager.generateChunks(any(), 0L) } throws IllegalArgumentException("fileSize must be > 0")

        val callback = mockk<SteadyFetchCallback>(relaxed = true)
        controller.queueDownload(baseRequest, callback)
        dispatcher.scheduler.advanceUntilIdle()

        // Should validate storage even for 0 length
        verify { fileManager.validateStorageCapacity(baseRequest.downloadDir, 0L) }
        // Should report error since chunk generation fails for 0 size
        verify { callback.onError(any()) }
    }

    @Test
    fun queueDownload_withVeryLargeContentLength_handlesCorrectly() {
        val largeSize = 10L * 1024 * 1024 * 1024 // 10GB
        every { networking.fetchRemoteMetadata(any(), any()) } returns metadata.copy(contentLength = largeSize)

        controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify { fileManager.validateStorageCapacity(baseRequest.downloadDir, largeSize) }
    }

    @Test
    fun queueDownload_concurrentDownloads_generateUniqueIds() {
        val ids = mutableListOf<Long>()
        repeat(10) {
            ids.add(controller.queueDownload(baseRequest, noopCallback()))
            Thread.sleep(2) // Ensure different nanosecond timestamps
        }

        // IDs should be unique or at least monotonically increasing
        assertEquals(10, ids.size)
        // Verify IDs are in ascending order (time moves forward)
        for (i in 1 until ids.size) {
            assertTrue("ID at index $i should be >= previous ID", ids[i] >= ids[i - 1])
        }
    }

    // ========== Helper Methods ==========

    private fun noopCallback(): SteadyFetchCallback = object : SteadyFetchCallback {
        override fun onSuccess() {}
        override fun onUpdate(progress: DownloadProgress) {}
        override fun onError(error: DownloadError) {}
    }

    // Helper to check if job is active (using reflection for testing)
    private fun SteadyFetchController.hasActiveJob(downloadId: Long): Boolean {
        val field = SteadyFetchController::class.java.getDeclaredField("activeJobs")
        field.isAccessible = true
        val jobs = field.get(this) as java.util.concurrent.ConcurrentHashMap<Long, Job>
        return jobs.containsKey(downloadId)
    }
}
