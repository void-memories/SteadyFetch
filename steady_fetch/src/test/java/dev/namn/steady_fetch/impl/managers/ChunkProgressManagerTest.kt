package dev.namn.steady_fetch.impl.managers

import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import dev.namn.steady_fetch.impl.datamodels.DownloadChunkProgress
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChunkProgressManagerTest {

    private fun createChunks(count: Int): List<DownloadChunk> {
        return (0 until count).map { index ->
            DownloadChunk("chunk$index", index * 100L, (index + 1) * 100L - 1)
        }
    }

    // ========== Initialization Tests ==========

    @Test
    fun constructor_initializesWithEmptyList() {
        val manager = ChunkProgressManager(emptyList())
        val snapshot = manager.snapshot()
        
        assertTrue(snapshot.isEmpty())
    }

    @Test
    fun constructor_initializesWithSingleChunk() {
        val chunks = listOf(DownloadChunk("single", 0, 100))
        val manager = ChunkProgressManager(chunks)
        val snapshot = manager.snapshot()
        
        assertEquals(1, snapshot.size)
        assertEquals(DownloadStatus.QUEUED, snapshot[0].status)
        assertEquals(0f, snapshot[0].progress, 0f)
        assertEquals("single", snapshot[0].name)
    }

    @Test
    fun constructor_initializesWithMultipleChunks() {
        val chunks = createChunks(5)
        val manager = ChunkProgressManager(chunks)
        val snapshot = manager.snapshot()
        
        assertEquals(5, snapshot.size)
        snapshot.forEach { progress ->
            assertEquals(DownloadStatus.QUEUED, progress.status)
            assertEquals(0f, progress.progress, 0f)
        }
    }

    @Test
    fun constructor_initializesAllChunksWithCorrectNames() {
        val chunks = listOf(
            DownloadChunk("chunk1", 0, 10),
            DownloadChunk("chunk2", 11, 20),
            DownloadChunk("chunk3", 21, 30)
        )
        val manager = ChunkProgressManager(chunks)
        val snapshot = manager.snapshot()
        
        assertEquals("chunk1", snapshot[0].name)
        assertEquals("chunk2", snapshot[1].name)
        assertEquals("chunk3", snapshot[2].name)
    }

    @Test
    fun constructor_initializesWithLargeNumberOfChunks() {
        val chunks = createChunks(100)
        val manager = ChunkProgressManager(chunks)
        val snapshot = manager.snapshot()
        
        assertEquals(100, snapshot.size)
        snapshot.forEachIndexed { index, progress ->
            assertEquals("chunk$index", progress.name)
            assertEquals(DownloadStatus.QUEUED, progress.status)
            assertEquals(0f, progress.progress, 0f)
        }
    }

    // ========== Snapshot Tests ==========

    @Test
    fun snapshot_returnsDefensiveCopy() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        val firstSnapshot = manager.snapshot()
        val secondSnapshot = manager.snapshot()
        
        assertNotSame(firstSnapshot, secondSnapshot)
        assertNotSame(firstSnapshot[0], secondSnapshot[0])
    }

    @Test
    fun snapshot_returnsImmutableCopy() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        val snapshot = manager.snapshot()
        
        manager.markRunning(0)
        val newSnapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.QUEUED, snapshot[0].status)
        assertEquals(DownloadStatus.RUNNING, newSnapshot[0].status)
    }

    @Test
    fun snapshot_returnsCurrentState() {
        val chunks = createChunks(3)
        val manager = ChunkProgressManager(chunks)
        
        manager.markRunning(0)
        manager.updateProgress(1, 0.5f)
        manager.markSuccess(2)
        
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.RUNNING, snapshot[0].status)
        assertEquals(0f, snapshot[0].progress, 0f)
        
        assertEquals(DownloadStatus.QUEUED, snapshot[1].status)
        assertEquals(0.5f, snapshot[1].progress, 0f)
        
        assertEquals(DownloadStatus.SUCCESS, snapshot[2].status)
        assertEquals(1f, snapshot[2].progress, 0f)
    }

    @Test
    fun snapshot_returnsEmptyListForEmptyManager() {
        val manager = ChunkProgressManager(emptyList())
        val snapshot = manager.snapshot()
        
        assertTrue(snapshot.isEmpty())
    }

    // ========== Seed Tests ==========

    @Test
    fun seed_updatesStatusAndProgress() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.seed(0, DownloadStatus.RUNNING, 0.5f)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.RUNNING, snapshot[0].status)
        assertEquals(0.5f, snapshot[0].progress, 0f)
    }

    @Test
    fun seed_updatesOnlyTargetChunk() {
        val chunks = createChunks(3)
        val manager = ChunkProgressManager(chunks)
        
        manager.seed(1, DownloadStatus.RUNNING, 0.7f)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.QUEUED, snapshot[0].status)
        assertEquals(0f, snapshot[0].progress, 0f)
        
        assertEquals(DownloadStatus.RUNNING, snapshot[1].status)
        assertEquals(0.7f, snapshot[1].progress, 0f)
        
        assertEquals(DownloadStatus.QUEUED, snapshot[2].status)
        assertEquals(0f, snapshot[2].progress, 0f)
    }

    @Test
    fun seed_withAllStatusTypes() {
        val chunks = createChunks(4)
        val manager = ChunkProgressManager(chunks)
        
        manager.seed(0, DownloadStatus.QUEUED, 0f)
        manager.seed(1, DownloadStatus.RUNNING, 0.3f)
        manager.seed(2, DownloadStatus.SUCCESS, 1f)
        manager.seed(3, DownloadStatus.FAILED, 0f)
        
        val snapshot = manager.snapshot()
        assertEquals(DownloadStatus.QUEUED, snapshot[0].status)
        assertEquals(DownloadStatus.RUNNING, snapshot[1].status)
        assertEquals(DownloadStatus.SUCCESS, snapshot[2].status)
        assertEquals(DownloadStatus.FAILED, snapshot[3].status)
    }

    @Test
    fun seed_withProgressBoundaries() {
        val chunks = createChunks(4)
        val manager = ChunkProgressManager(chunks)
        
        manager.seed(0, DownloadStatus.RUNNING, 0f)
        manager.seed(1, DownloadStatus.RUNNING, 0.5f)
        manager.seed(2, DownloadStatus.RUNNING, 1f)
        manager.seed(3, DownloadStatus.RUNNING, 0.999f)
        
        val snapshot = manager.snapshot()
        assertEquals(0f, snapshot[0].progress, 0f)
        assertEquals(0.5f, snapshot[1].progress, 0f)
        assertEquals(1f, snapshot[2].progress, 0f)
        assertEquals(0.999f, snapshot[3].progress, 0f)
    }

    @Test
    fun seed_withNegativeProgress() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        // Negative progress should be allowed (no validation in code)
        manager.seed(0, DownloadStatus.RUNNING, -0.5f)
        val snapshot = manager.snapshot()
        
        assertEquals(-0.5f, snapshot[0].progress, 0f)
    }

    @Test
    fun seed_withProgressGreaterThanOne() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        // Progress > 1.0 should be allowed (no validation in code)
        manager.seed(0, DownloadStatus.RUNNING, 1.5f)
        val snapshot = manager.snapshot()
        
        assertEquals(1.5f, snapshot[0].progress, 0f)
    }

    @Test
    fun seed_updatesStateCorrectly() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.seed(0, DownloadStatus.RUNNING, 0.5f)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.RUNNING, snapshot[0].status)
        assertEquals(0.5f, snapshot[0].progress, 0f)
    }

    // ========== MarkRunning Tests ==========

    @Test
    fun markRunning_setsStatusToRunning() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.markRunning(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.RUNNING, snapshot[0].status)
    }

    @Test
    fun markRunning_preservesProgress() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 0.5f)
        manager.markRunning(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.RUNNING, snapshot[0].status)
        assertEquals(0.5f, snapshot[0].progress, 0f)
    }

    @Test
    fun markRunning_doesNotAffectOtherChunks() {
        val chunks = createChunks(3)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 0.3f)
        manager.updateProgress(2, 0.7f)
        manager.markRunning(1)
        
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.QUEUED, snapshot[0].status)
        assertEquals(0.3f, snapshot[0].progress, 0f)
        
        assertEquals(DownloadStatus.RUNNING, snapshot[1].status)
        assertEquals(0f, snapshot[1].progress, 0f)
        
        assertEquals(DownloadStatus.QUEUED, snapshot[2].status)
        assertEquals(0.7f, snapshot[2].progress, 0f)
    }

    @Test
    fun markRunning_fromQueuedStatus() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.markRunning(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.RUNNING, snapshot[0].status)
    }

    @Test
    fun markRunning_fromFailedStatus() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.markFailed(0)
        manager.markRunning(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.RUNNING, snapshot[0].status)
    }

    @Test
    fun markRunning_returnsUpdatedSnapshot() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        val result = manager.markRunning(0)
        val snapshot = manager.snapshot()
        
        assertEquals(result, snapshot)
    }

    // ========== UpdateProgress Tests ==========

    @Test
    fun updateProgress_updatesProgressValue() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 0.5f)
        val snapshot = manager.snapshot()
        
        assertEquals(0.5f, snapshot[0].progress, 0f)
    }

    @Test
    fun updateProgress_preservesExistingStatus() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.markRunning(0)
        manager.updateProgress(0, 0.5f)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.RUNNING, snapshot[0].status)
        assertEquals(0.5f, snapshot[0].progress, 0f)
    }

    @Test
    fun updateProgress_doesNotAffectOtherChunks() {
        val chunks = createChunks(3)
        val manager = ChunkProgressManager(chunks)
        
        manager.markRunning(1)
        manager.updateProgress(0, 0.5f)
        val snapshot = manager.snapshot()
        
        assertEquals(0.5f, snapshot[0].progress, 0f)
        assertEquals(DownloadStatus.RUNNING, snapshot[1].status)
        assertEquals(0f, snapshot[1].progress, 0f)
        assertEquals(0f, snapshot[2].progress, 0f)
    }

    @Test
    fun updateProgress_withZeroProgress() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 0.5f)
        manager.updateProgress(0, 0f)
        val snapshot = manager.snapshot()
        
        assertEquals(0f, snapshot[0].progress, 0f)
    }

    @Test
    fun updateProgress_withOneProgress() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 1f)
        val snapshot = manager.snapshot()
        
        assertEquals(1f, snapshot[0].progress, 0f)
    }

    @Test
    fun updateProgress_withNegativeProgress() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, -0.1f)
        val snapshot = manager.snapshot()
        
        assertEquals(-0.1f, snapshot[0].progress, 0f)
    }

    @Test
    fun updateProgress_withProgressGreaterThanOne() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 1.5f)
        val snapshot = manager.snapshot()
        
        assertEquals(1.5f, snapshot[0].progress, 0f)
    }

    @Test
    fun updateProgress_withIncrementalUpdates() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 0.1f)
        assertEquals(0.1f, manager.snapshot()[0].progress, 0f)
        
        manager.updateProgress(0, 0.3f)
        assertEquals(0.3f, manager.snapshot()[0].progress, 0f)
        
        manager.updateProgress(0, 0.7f)
        assertEquals(0.7f, manager.snapshot()[0].progress, 0f)
        
        manager.updateProgress(0, 1f)
        assertEquals(1f, manager.snapshot()[0].progress, 0f)
    }

    @Test
    fun updateProgress_returnsUpdatedSnapshot() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        val result = manager.updateProgress(0, 0.5f)
        val snapshot = manager.snapshot()
        
        assertEquals(result, snapshot)
    }

    // ========== MarkSuccess Tests ==========

    @Test
    fun markSuccess_setsStatusToSuccess() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.markSuccess(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.SUCCESS, snapshot[0].status)
    }

    @Test
    fun markSuccess_setsProgressToOne() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.markSuccess(0)
        val snapshot = manager.snapshot()
        
        assertEquals(1f, snapshot[0].progress, 0f)
    }

    @Test
    fun markSuccess_overwritesExistingProgress() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 0.5f)
        manager.markSuccess(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.SUCCESS, snapshot[0].status)
        assertEquals(1f, snapshot[0].progress, 0f)
    }

    @Test
    fun markSuccess_doesNotAffectOtherChunks() {
        val chunks = createChunks(3)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 0.3f)
        manager.updateProgress(2, 0.7f)
        manager.markSuccess(1)
        
        val snapshot = manager.snapshot()
        
        assertEquals(0.3f, snapshot[0].progress, 0f)
        assertEquals(DownloadStatus.SUCCESS, snapshot[1].status)
        assertEquals(1f, snapshot[1].progress, 0f)
        assertEquals(0.7f, snapshot[2].progress, 0f)
    }

    @Test
    fun markSuccess_fromQueuedStatus() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.markSuccess(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.SUCCESS, snapshot[0].status)
        assertEquals(1f, snapshot[0].progress, 0f)
    }

    @Test
    fun markSuccess_fromRunningStatus() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.markRunning(0)
        manager.updateProgress(0, 0.5f)
        manager.markSuccess(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.SUCCESS, snapshot[0].status)
        assertEquals(1f, snapshot[0].progress, 0f)
    }

    @Test
    fun markSuccess_returnsUpdatedSnapshot() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        val result = manager.markSuccess(0)
        val snapshot = manager.snapshot()
        
        assertEquals(result, snapshot)
    }

    // ========== MarkFailed Tests ==========

    @Test
    fun markFailed_setsStatusToFailed() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.markFailed(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.FAILED, snapshot[0].status)
    }

    @Test
    fun markFailed_setsProgressToZero() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.markFailed(0)
        val snapshot = manager.snapshot()
        
        assertEquals(0f, snapshot[0].progress, 0f)
    }

    @Test
    fun markFailed_overwritesExistingProgress() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 0.8f)
        manager.markFailed(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.FAILED, snapshot[0].status)
        assertEquals(0f, snapshot[0].progress, 0f)
    }

    @Test
    fun markFailed_doesNotAffectOtherChunks() {
        val chunks = createChunks(3)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 0.3f)
        manager.updateProgress(2, 0.7f)
        manager.markFailed(1)
        
        val snapshot = manager.snapshot()
        
        assertEquals(0.3f, snapshot[0].progress, 0f)
        assertEquals(DownloadStatus.FAILED, snapshot[1].status)
        assertEquals(0f, snapshot[1].progress, 0f)
        assertEquals(0.7f, snapshot[2].progress, 0f)
    }

    @Test
    fun markFailed_fromQueuedStatus() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.markFailed(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.FAILED, snapshot[0].status)
        assertEquals(0f, snapshot[0].progress, 0f)
    }

    @Test
    fun markFailed_fromRunningStatus() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.markRunning(0)
        manager.updateProgress(0, 0.9f)
        manager.markFailed(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.FAILED, snapshot[0].status)
        assertEquals(0f, snapshot[0].progress, 0f)
    }

    @Test
    fun markFailed_fromSuccessStatus() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.markSuccess(0)
        manager.markFailed(0)
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.FAILED, snapshot[0].status)
        assertEquals(0f, snapshot[0].progress, 0f)
    }

    @Test
    fun markFailed_returnsUpdatedSnapshot() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        val result = manager.markFailed(0)
        val snapshot = manager.snapshot()
        
        assertEquals(result, snapshot)
    }

    // ========== Index Validation Tests ==========

    @Test(expected = IndexOutOfBoundsException::class)
    fun update_withNegativeIndex_throwsException() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        manager.markRunning(-1)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun update_withIndexEqualToSize_throwsException() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        manager.markRunning(2)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun update_withIndexGreaterThanSize_throwsException() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        manager.markRunning(10)
    }

    @Test
    fun update_withValidBoundaryIndices() {
        val chunks = createChunks(3)
        val manager = ChunkProgressManager(chunks)
        
        // First index
        manager.markRunning(0)
        assertEquals(DownloadStatus.RUNNING, manager.snapshot()[0].status)
        
        // Last index
        manager.markSuccess(2)
        assertEquals(DownloadStatus.SUCCESS, manager.snapshot()[2].status)
    }

    // ========== State Transition Tests ==========

    @Test
    fun stateTransition_queuedToRunningToSuccess() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        assertEquals(DownloadStatus.QUEUED, manager.snapshot()[0].status)
        
        manager.markRunning(0)
        assertEquals(DownloadStatus.RUNNING, manager.snapshot()[0].status)
        
        manager.markSuccess(0)
        assertEquals(DownloadStatus.SUCCESS, manager.snapshot()[0].status)
        assertEquals(1f, manager.snapshot()[0].progress, 0f)
    }

    @Test
    fun stateTransition_queuedToRunningToFailed() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        assertEquals(DownloadStatus.QUEUED, manager.snapshot()[0].status)
        
        manager.markRunning(0)
        assertEquals(DownloadStatus.RUNNING, manager.snapshot()[0].status)
        
        manager.markFailed(0)
        assertEquals(DownloadStatus.FAILED, manager.snapshot()[0].status)
        assertEquals(0f, manager.snapshot()[0].progress, 0f)
    }

    @Test
    fun stateTransition_failedToRunningToSuccess() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.markFailed(0)
        assertEquals(DownloadStatus.FAILED, manager.snapshot()[0].status)
        
        manager.markRunning(0)
        assertEquals(DownloadStatus.RUNNING, manager.snapshot()[0].status)
        
        manager.markSuccess(0)
        assertEquals(DownloadStatus.SUCCESS, manager.snapshot()[0].status)
    }

    @Test
    fun stateTransition_successToFailed() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.markSuccess(0)
        assertEquals(DownloadStatus.SUCCESS, manager.snapshot()[0].status)
        
        manager.markFailed(0)
        assertEquals(DownloadStatus.FAILED, manager.snapshot()[0].status)
        assertEquals(0f, manager.snapshot()[0].progress, 0f)
    }

    // ========== Multiple Chunks Update Tests ==========

    @Test
    fun updateMultipleChunks_independently() {
        val chunks = createChunks(5)
        val manager = ChunkProgressManager(chunks)
        
        manager.markRunning(0)
        manager.updateProgress(1, 0.2f)
        manager.markSuccess(2)
        manager.markFailed(3)
        manager.updateProgress(4, 0.9f)
        
        val snapshot = manager.snapshot()
        
        assertEquals(DownloadStatus.RUNNING, snapshot[0].status)
        assertEquals(0f, snapshot[0].progress, 0f)
        
        assertEquals(DownloadStatus.QUEUED, snapshot[1].status)
        assertEquals(0.2f, snapshot[1].progress, 0f)
        
        assertEquals(DownloadStatus.SUCCESS, snapshot[2].status)
        assertEquals(1f, snapshot[2].progress, 0f)
        
        assertEquals(DownloadStatus.FAILED, snapshot[3].status)
        assertEquals(0f, snapshot[3].progress, 0f)
        
        assertEquals(DownloadStatus.QUEUED, snapshot[4].status)
        assertEquals(0.9f, snapshot[4].progress, 0f)
    }

    @Test
    fun updateSameChunk_multipleTimes() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.markRunning(0)
        manager.updateProgress(0, 0.1f)
        manager.updateProgress(0, 0.3f)
        manager.updateProgress(0, 0.5f)
        manager.updateProgress(0, 0.8f)
        manager.markSuccess(0)
        
        val snapshot = manager.snapshot()
        assertEquals(DownloadStatus.SUCCESS, snapshot[0].status)
        assertEquals(1f, snapshot[0].progress, 0f)
    }

    // ========== Thread Safety Tests ==========

    @Test
    fun concurrentUpdates_threadSafe() {
        val chunks = createChunks(10)
        val manager = ChunkProgressManager(chunks)
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(10)
        
        try {
            repeat(10) { index ->
                executor.submit {
                    try {
                        manager.markRunning(index)
                        manager.updateProgress(index, 0.5f)
                        manager.markSuccess(index)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            
            val snapshot = manager.snapshot()
            assertEquals(10, snapshot.size)
            snapshot.forEach { progress ->
                assertEquals(DownloadStatus.SUCCESS, progress.status)
                assertEquals(1f, progress.progress, 0f)
            }
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun concurrentSnapshots_threadSafe() {
        val chunks = createChunks(5)
        val manager = ChunkProgressManager(chunks)
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(10)
        
        try {
            repeat(10) {
                executor.submit {
                    try {
                        repeat(100) {
                            manager.snapshot()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
            
            assertTrue(latch.await(5, TimeUnit.SECONDS))
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun concurrentUpdatesAndSnapshots_threadSafe() {
        val chunks = createChunks(5)
        val manager = ChunkProgressManager(chunks)
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(10)
        
        try {
            repeat(5) { index ->
                executor.submit {
                    try {
                        repeat(10) {
                            manager.updateProgress(index, Math.random().toFloat())
                            manager.snapshot()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
            
            repeat(5) {
                executor.submit {
                    try {
                        repeat(10) {
                            manager.snapshot()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
            
            assertTrue(latch.await(5, TimeUnit.SECONDS))
        } finally {
            executor.shutdown()
        }
    }

    // ========== Immutability Tests ==========

    @Test
    fun snapshot_returnsNewListInstance() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        val snapshot1 = manager.snapshot()
        val snapshot2 = manager.snapshot()
        
        assertNotSame(snapshot1, snapshot2)
    }

    @Test
    fun snapshot_returnsNewProgressInstances() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        val snapshot1 = manager.snapshot()
        manager.markRunning(0)
        val snapshot2 = manager.snapshot()
        
        assertNotSame(snapshot1[0], snapshot2[0])
        assertNotSame(snapshot1[1], snapshot2[1])
    }

    @Test
    fun updateMethods_returnNewInstances() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        val result1 = manager.markRunning(0)
        val result2 = manager.updateProgress(0, 0.5f)
        
        assertNotSame(result1, result2)
        assertNotSame(result1[0], result2[0])
    }

    // ========== Edge Cases for Progress Values ==========

    @Test
    fun progress_withFloatPrecision() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, 0.123456789f)
        val snapshot = manager.snapshot()
        
        assertEquals(0.123456789f, snapshot[0].progress, 0.0000001f)
    }

    @Test
    fun progress_withVerySmallValue() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, Float.MIN_VALUE)
        val snapshot = manager.snapshot()
        
        assertEquals(Float.MIN_VALUE, snapshot[0].progress, 0f)
    }

    @Test
    fun progress_withVeryLargeValue() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, Float.MAX_VALUE)
        val snapshot = manager.snapshot()
        
        assertEquals(Float.MAX_VALUE, snapshot[0].progress, 0f)
    }

    @Test
    fun progress_withNaN() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, Float.NaN)
        val snapshot = manager.snapshot()
        
        assertTrue(snapshot[0].progress.isNaN())
    }

    @Test
    fun progress_withPositiveInfinity() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, Float.POSITIVE_INFINITY)
        val snapshot = manager.snapshot()
        
        assertTrue(snapshot[0].progress.isInfinite())
    }

    @Test
    fun progress_withNegativeInfinity() {
        val chunks = createChunks(1)
        val manager = ChunkProgressManager(chunks)
        
        manager.updateProgress(0, Float.NEGATIVE_INFINITY)
        val snapshot = manager.snapshot()
        
        assertTrue(snapshot[0].progress.isInfinite())
        assertTrue(snapshot[0].progress < 0)
    }

    // ========== Comprehensive Integration Tests ==========

    @Test
    fun comprehensiveWorkflow_completeDownload() {
        val chunks = createChunks(5)
        val manager = ChunkProgressManager(chunks)
        
        // Initial state
        var snapshot = manager.snapshot()
        assertEquals(5, snapshot.size)
        snapshot.forEach {
            assertEquals(DownloadStatus.QUEUED, it.status)
            assertEquals(0f, it.progress, 0f)
        }
        
        // Start downloading chunks 0, 1, 2
        manager.markRunning(0)
        manager.markRunning(1)
        manager.markRunning(2)
        
        snapshot = manager.snapshot()
        assertEquals(DownloadStatus.RUNNING, snapshot[0].status)
        assertEquals(DownloadStatus.RUNNING, snapshot[1].status)
        assertEquals(DownloadStatus.RUNNING, snapshot[2].status)
        
        // Update progress
        manager.updateProgress(0, 0.3f)
        manager.updateProgress(1, 0.5f)
        manager.updateProgress(2, 0.7f)
        
        snapshot = manager.snapshot()
        assertEquals(0.3f, snapshot[0].progress, 0f)
        assertEquals(0.5f, snapshot[1].progress, 0f)
        assertEquals(0.7f, snapshot[2].progress, 0f)
        
        // Complete chunks
        manager.markSuccess(0)
        manager.markSuccess(1)
        manager.markSuccess(2)
        
        snapshot = manager.snapshot()
        assertEquals(DownloadStatus.SUCCESS, snapshot[0].status)
        assertEquals(1f, snapshot[0].progress, 0f)
        assertEquals(DownloadStatus.SUCCESS, snapshot[1].status)
        assertEquals(1f, snapshot[1].progress, 0f)
        assertEquals(DownloadStatus.SUCCESS, snapshot[2].status)
        assertEquals(1f, snapshot[2].progress, 0f)
        
        // Start remaining chunks
        manager.markRunning(3)
        manager.markRunning(4)
        
        // One fails, retry it
        manager.markFailed(3)
        manager.markRunning(3)
        manager.updateProgress(3, 0.8f)
        manager.markSuccess(3)
        
        manager.updateProgress(4, 1f)
        manager.markSuccess(4)
        
        snapshot = manager.snapshot()
        snapshot.forEach {
            assertEquals(DownloadStatus.SUCCESS, it.status)
            assertEquals(1f, it.progress, 0f)
        }
    }

    @Test
    fun comprehensiveWorkflow_withFailuresAndRetries() {
        val chunks = createChunks(3)
        val manager = ChunkProgressManager(chunks)
        
        // Start all chunks
        manager.markRunning(0)
        manager.markRunning(1)
        manager.markRunning(2)
        
        // Chunk 0 fails
        manager.markFailed(0)
        
        // Chunk 1 succeeds
        manager.updateProgress(1, 0.5f)
        manager.updateProgress(1, 1f)
        manager.markSuccess(1)
        
        // Chunk 2 fails
        manager.markFailed(2)
        
        var snapshot = manager.snapshot()
        assertEquals(DownloadStatus.FAILED, snapshot[0].status)
        assertEquals(DownloadStatus.SUCCESS, snapshot[1].status)
        assertEquals(DownloadStatus.FAILED, snapshot[2].status)
        
        // Retry failed chunks
        manager.markRunning(0)
        manager.updateProgress(0, 0.6f)
        manager.markSuccess(0)
        
        manager.markRunning(2)
        manager.updateProgress(2, 0.9f)
        manager.markSuccess(2)
        
        snapshot = manager.snapshot()
        snapshot.forEach {
            assertEquals(DownloadStatus.SUCCESS, it.status)
            assertEquals(1f, it.progress, 0f)
        }
    }

    // ========== Return Value Consistency Tests ==========

    @Test
    fun allUpdateMethods_returnConsistentSnapshots() {
        val chunks = createChunks(3)
        val manager = ChunkProgressManager(chunks)
        
        manager.seed(0, DownloadStatus.RUNNING, 0.5f)
        val runningResult = manager.markRunning(1)
        val progressResult = manager.updateProgress(2, 0.3f)
        val successResult = manager.markSuccess(0)
        val failedResult = manager.markFailed(1)
        
        val finalSnapshot = manager.snapshot()
        
        // Verify final state after all operations
        assertEquals(DownloadStatus.SUCCESS, finalSnapshot[0].status)
        assertEquals(1f, finalSnapshot[0].progress, 0f)
        assertEquals(DownloadStatus.FAILED, finalSnapshot[1].status)
        assertEquals(0f, finalSnapshot[1].progress, 0f)
        assertEquals(DownloadStatus.QUEUED, finalSnapshot[2].status)
        assertEquals(0.3f, finalSnapshot[2].progress, 0f)
        
        // Each update method returns a snapshot that matches the state at that point
        // The last result (failedResult) should match the final snapshot
        assertEquals(finalSnapshot, failedResult)
        
        // Verify each result is a proper snapshot (independent copy)
        assertNotSame(runningResult, progressResult)
        assertNotSame(progressResult, successResult)
        assertNotSame(successResult, failedResult)
    }

    @Test
    fun returnedSnapshots_areIndependentCopies() {
        val chunks = createChunks(2)
        val manager = ChunkProgressManager(chunks)
        
        val result1 = manager.markRunning(0)
        val result2 = manager.updateProgress(1, 0.5f)
        
        assertNotSame(result1, result2)
        assertNotSame(result1[0], result2[0])
        assertNotSame(result1[1], result2[1])
    }
}
