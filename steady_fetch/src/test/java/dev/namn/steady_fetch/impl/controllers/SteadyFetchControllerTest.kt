package dev.namn.steady_fetch.impl.controllers

import dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback
import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import dev.namn.steady_fetch.impl.datamodels.DownloadMetadata
import dev.namn.steady_fetch.impl.datamodels.DownloadRequest
import dev.namn.steady_fetch.impl.datamodels.DownloadError
import dev.namn.steady_fetch.impl.datamodels.DownloadProgress
import dev.namn.steady_fetch.impl.io.Networking
import dev.namn.steady_fetch.impl.managers.ChunkManager
import dev.namn.steady_fetch.impl.managers.FileManager
import dev.namn.steady_fetch.impl.notifications.DownloadNotificationManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

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
        contentLength = 100,
        supportsRanges = true,
        contentMd5 = "abc"
    )
    private val chunks = listOf(DownloadChunk("file.part1", 0, 49), DownloadChunk("file.part2", 50, 99))

    @Before
    fun setUp() {
        every { networking.fetchRemoteMetadata(any(), any()) } returns metadata
        coEvery { networking.download(any(), any(), any()) } just Runs
        every { chunkManager.generateChunks(any(), any()) } returns chunks
        every { fileManager.createDirectoryIfNotExists(any()) } just Runs
    }

    @After
    fun tearDown() {
        downloadDir.deleteRecursively()
    }

    @Test
    fun queueDownload_buildsMetadataWithChunksWhenRangesSupported() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            networking.download(
                id,
                match<DownloadMetadata> { it.chunks == chunks && it.request == baseRequest },
                any()
            )
        }
    }

    @Test
    fun queueDownload_validatesStorageWhenSizeKnown() {
        controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify { fileManager.validateStorageCapacity(baseRequest.downloadDir, metadata.contentLength!!) }
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
    fun queueDownload_failsFastWhenParallelLimitInvalid() {
        val badRequest = baseRequest.copy(maxParallelDownloads = 0)

        val result = runCatching { controller.queueDownload(badRequest, noopCallback()) }

        assertTrue(result.isFailure)
    }

    @Test
    fun queueDownload_reportsSetupFailuresThroughCallback() {
        val callback = mockk<SteadyFetchCallback>(relaxed = true)
        val failure = IllegalStateException("boom")
        every { fileManager.createDirectoryIfNotExists(any()) } throws failure

        controller.queueDownload(baseRequest, callback)
        dispatcher.scheduler.advanceUntilIdle()

        verify {
            callback.onError(match { it.code != 0 && it.message.contains("boom") })
        }
        coVerify(exactly = 0) { networking.download(any(), any(), any()) }
    }

    @Test
    fun cancel_cancelsJobAndNetworking() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        every { networking.cancel(id) } returns true

        val cancelled = controller.cancel(id)

        assertTrue(cancelled)
        verify { networking.cancel(id) }
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

    private fun noopCallback(): SteadyFetchCallback = object : SteadyFetchCallback {
        override fun onSuccess() {}
        override fun onUpdate(progress: DownloadProgress) {}
        override fun onError(error: DownloadError) {}
    }
}

