package dev.namn.steady_fetch.impl.datamodels

import dev.namn.steady_fetch.impl.utils.Constants
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadModelsTest {

    @Test
    fun downloadRequest_defaultsAreApplied() {
        val request = DownloadRequest(
            url = "https://example.com/file.bin",
            downloadDir = File("/tmp"),
            fileName = "file.bin"
        )
        assertTrue(request.headers.isEmpty())
        assertEquals(4, request.maxParallelDownloads)
    }

    @Test(expected = IllegalArgumentException::class)
    fun downloadRequest_rejectsParallelLimitAboveMax() {
        DownloadRequest(
            url = "https://example.com/file.bin",
            downloadDir = File("/tmp"),
            fileName = "file.bin",
            maxParallelDownloads = Constants.MAX_PARALLEL_CHUNKS + 1
        )
    }

    @Test
    fun downloadChunkProgress_updateProgress_createsNewInstance() {
        val original = DownloadChunkProgress(
            status = DownloadStatus.QUEUED,
            name = "chunk-1",
            progress = 0f
        )
        val updated = original.updateProgress(0.75f)
        assertEquals(0f, original.progress, 0f)
        assertEquals(0.75f, updated.progress, 0f)
        assertNotEquals(original, updated)
    }

    @Test
    fun downloadChunkProgress_updateStatus_createsNewInstance() {
        val original = DownloadChunkProgress(
            status = DownloadStatus.QUEUED,
            name = "chunk-1",
            progress = 0.5f
        )
        val updated = original.updateStatus(DownloadStatus.SUCCESS)
        assertEquals(DownloadStatus.QUEUED, original.status)
        assertEquals(DownloadStatus.SUCCESS, updated.status)
        assertEquals(0.5f, updated.progress, 0f)
    }
}

