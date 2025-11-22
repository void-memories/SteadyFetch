package dev.namn.steady_fetch.impl.managers

import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkProgressManagerTest {

    private val chunks = listOf(
        DownloadChunk("one", 0, 10),
        DownloadChunk("two", 11, 20)
    )

    @Test
    fun snapshot_returnsDefensiveCopy() {
        val manager = ChunkProgressManager(chunks)
        val firstSnapshot = manager.snapshot()

        manager.markRunning(0)
        val secondSnapshot = manager.snapshot()

        assertNotSame(firstSnapshot, secondSnapshot)
        assertEquals(DownloadStatus.QUEUED, firstSnapshot.first().status)
        assertEquals(DownloadStatus.RUNNING, secondSnapshot.first().status)
    }

    @Test
    fun markSuccess_updatesProgressAndStatus() {
        val manager = ChunkProgressManager(chunks)
        val snapshot = manager.markSuccess(1)

        assertEquals(DownloadStatus.SUCCESS, snapshot[1].status)
        assertEquals(1f, snapshot[1].progress, 0f)
    }

    @Test
    fun markFailed_setsFailedStatusAndZeroProgress() {
        val manager = ChunkProgressManager(chunks)
        val snapshot = manager.markFailed(0)

        assertEquals(DownloadStatus.FAILED, snapshot[0].status)
        assertEquals(0f, snapshot[0].progress, 0f)
    }

    @Test
    fun updateProgress_preservesExistingStatus() {
        val manager = ChunkProgressManager(chunks)
        manager.markRunning(0)
        val snapshot = manager.updateProgress(0, 0.5f)

        assertEquals(DownloadStatus.RUNNING, snapshot[0].status)
        assertEquals(0.5f, snapshot[0].progress, 0f)
        assertTrue(snapshot[1].progress == 0f)
    }
}

