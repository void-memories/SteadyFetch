package dev.namn.steady_fetch.impl.controllers

import ChunkManager
import dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback
import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import dev.namn.steady_fetch.impl.datamodels.DownloadMetadata
import dev.namn.steady_fetch.impl.datamodels.DownloadRequest
import dev.namn.steady_fetch.impl.io.Networking
import dev.namn.steady_fetch.impl.managers.FileManager
import dev.namn.steady_fetch.impl.notifications.DownloadNotificationManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
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
        every { networking.download(any(), any(), any()) } just Runs
        every { chunkManager.generateChunks(any(), any()) } returns chunks
    }

    @After
    fun tearDown() {
        downloadDir.deleteRecursively()
    }

    @Test
    fun queueDownload_buildsMetadataWithChunksWhenRangesSupported() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        dispatcher.scheduler.advanceUntilIdle()

        verify {
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

    @Test(expected = Exception::class)
    fun queueDownload_throwsWhenParallelLimitExceeded() {
        val request = baseRequest.copy(maxParallelDownloads = 26)
        controller.queueDownload(request, noopCallback())
    }

    @Test
    fun cancel_cancelsJobAndNetworking() {
        val id = controller.queueDownload(baseRequest, noopCallback())
        every { networking.cancel(id) } returns true

        val cancelled = controller.cancel(id)

        assertTrue(cancelled)
        verify { networking.cancel(id) }
    }

    private fun noopCallback(): SteadyFetchCallback = object : SteadyFetchCallback {
        override fun onSuccess() {}
        override fun onUpdate(progress: dev.namn.steady_fetch.impl.datamodels.DownloadProgress) {}
        override fun onError(error: dev.namn.steady_fetch.impl.datamodels.DownloadError) {}
    }
}

