package dev.namn.steady_fetch.models

import android.app.Application
import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import java.io.File

/**
 * Unit tests for SteadyFetchController
 */
class SteadyFetchControllerTest {

    private lateinit var mockApplication: Application
    private lateinit var steadyFetchController: SteadyFetchController

    @Before
    fun setup() {
        mockApplication = mockk<Application>(relaxed = true)
        steadyFetchController = SteadyFetchController(mockApplication)
    }

    // Tests for divideDownloadIntoChunks

    @Test
    fun divideDownloadIntoChunks_perfectDivision_noRemainder() {
        // Given
        val totalBytes = 100L
        val maxChunks = 4

        // When
        val chunks = steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)

        // Then
        assertEquals(4, chunks.size)
        assertEquals(0L..24L, chunks[0])
        assertEquals(25L..49L, chunks[1])
        assertEquals(50L..74L, chunks[2])
        assertEquals(75L..99L, chunks[3])
        verifyChunksCoverAllBytes(chunks, totalBytes)
    }

    @Test
    fun divideDownloadIntoChunks_withRemainder_remainderDistributedToFirstChunks() {
        // Given
        val totalBytes = 100L
        val maxChunks = 3 // 100 / 3 = 33 remainder 1

        // When
        val chunks = steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)

        // Then
        assertEquals(3, chunks.size)
        assertEquals(0L..33L, chunks[0]) // 34 bytes (33 + 1 remainder)
        assertEquals(34L..66L, chunks[1]) // 33 bytes
        assertEquals(67L..99L, chunks[2]) // 33 bytes
        verifyChunksCoverAllBytes(chunks, totalBytes)
    }

    @Test
    fun divideDownloadIntoChunks_smallFile_smallerThanMaxChunks() {
        // Given
        val totalBytes = 3L
        val maxChunks = 10

        // When
        val chunks = steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)

        // Then
        assertEquals(3, chunks.size) // Should only create 3 chunks (one per byte)
        assertEquals(0L..0L, chunks[0])
        assertEquals(1L..1L, chunks[1])
        assertEquals(2L..2L, chunks[2])
        verifyChunksCoverAllBytes(chunks, totalBytes)
    }

    @Test
    fun divideDownloadIntoChunks_singleByte_file() {
        // Given
        val totalBytes = 1L
        val maxChunks = 4

        // When
        val chunks = steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)

        // Then
        assertEquals(1, chunks.size)
        assertEquals(0L..0L, chunks[0])
        verifyChunksCoverAllBytes(chunks, totalBytes)
    }

    @Test
    fun divideDownloadIntoChunks_maxSupportedChunksLimitsResult() {
        // Given
        val totalBytes = 1000L
        val maxChunks = 20
        val maxSupportedChunks = 8

        // When
        val chunks = steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks, maxSupportedChunks)

        // Then
        assertEquals(8, chunks.size) // Should be limited by maxSupportedChunks
        verifyChunksCoverAllBytes(chunks, totalBytes)
    }

    @Test
    fun divideDownloadIntoChunks_maxChunksLimitsResult() {
        // Given
        val totalBytes = 1000L
        val maxChunks = 4
        val maxSupportedChunks = 16

        // When
        val chunks = steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks, maxSupportedChunks)

        // Then
        assertEquals(4, chunks.size) // Should be limited by maxChunks
        verifyChunksCoverAllBytes(chunks, totalBytes)
    }

    @Test
    fun divideDownloadIntoChunks_defaultMaxSupportedChunks() {
        // Given
        val totalBytes = 100L
        val maxChunks = 20

        // When
        val chunks = steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)

        // Then
        assertEquals(8, chunks.size) // Should use default maxSupportedChunks = 8
        verifyChunksCoverAllBytes(chunks, totalBytes)
    }

    @Test
    fun divideDownloadIntoChunks_largeFile_withManyChunks() {
        // Given
        val totalBytes = 10000L
        val maxChunks = 16
        val maxSupportedChunks = 16

        // When
        val chunks = steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks, maxSupportedChunks)

        // Then
        assertEquals(16, chunks.size)
        verifyChunksCoverAllBytes(chunks, totalBytes)
        // Verify all chunks are approximately equal size (within 1 byte)
        val sizes = chunks.map { it.last - it.first + 1 }
        val minSize = sizes.minOrNull() ?: 0
        val maxSize = sizes.maxOrNull() ?: 0
        assertTrue("Size difference should be at most 1", maxSize - minSize <= 1)
    }

    @Test
    fun divideDownloadIntoChunks_zeroTotalBytes_throwsException() {
        // Given
        val totalBytes = 0L
        val maxChunks = 4

        // When/Then
        try {
            steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error message should mention totalBytes", e.message?.contains("totalBytes") == true)
        }
    }

    @Test
    fun divideDownloadIntoChunks_zeroMaxChunks_throwsException() {
        // Given
        val totalBytes = 100L
        val maxChunks = 0

        // When/Then
        try {
            steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error message should mention maxChunks", e.message?.contains("maxChunks") == true)
        }
    }

    @Test
    fun divideDownloadIntoChunks_negativeMaxChunks_throwsException() {
        // Given
        val totalBytes = 100L
        val maxChunks = -1

        // When/Then
        try {
            steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error message should mention maxChunks", e.message?.contains("maxChunks") == true)
        }
    }

    @Test
    fun divideDownloadIntoChunks_negativeTotalBytes_throwsException() {
        // Given
        val totalBytes = -100L
        val maxChunks = 4

        // When/Then
        try {
            steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error message should mention totalBytes", e.message?.contains("totalBytes") == true)
        }
    }

    @Test
    fun divideDownloadIntoChunks_chunksAreContiguous_noGaps() {
        // Given
        val totalBytes = 100L
        val maxChunks = 5

        // When
        val chunks = steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)

        // Then
        for (i in 0 until chunks.size - 1) {
            val currentEnd = chunks[i].last
            val nextStart = chunks[i + 1].first
            assertEquals("Chunks should be contiguous", currentEnd + 1, nextStart)
        }
    }

    @Test
    fun divideDownloadIntoChunks_chunksDoNotOverlap() {
        // Given
        val totalBytes = 100L
        val maxChunks = 5

        // When
        val chunks = steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)

        // Then
        for (i in chunks.indices) {
            for (j in chunks.indices) {
                if (i != j) {
                    val range1 = chunks[i]
                    val range2 = chunks[j]
                    // Ranges should not overlap
                    assertTrue(
                        "Ranges should not overlap: $range1 and $range2",
                        range1.last < range2.first || range2.last < range1.first
                    )
                }
            }
        }
    }

    @Test
    fun divideDownloadIntoChunks_remainderDistribution_firstChunksGetExtraByte() {
        // Given: 13 bytes, 3 chunks = 4, 4, 5 (remainder of 1)
        val totalBytes = 13L
        val maxChunks = 3

        // When
        val chunks = steadyFetchController.divideDownloadIntoChunks(totalBytes, maxChunks)

        // Then
        assertEquals(3, chunks.size)
        val sizes = chunks.map { it.last - it.first + 1 }
        // First chunk should get the remainder
        assertEquals(5, sizes[0]) // 13/3 = 4 remainder 1, so first gets 5
        assertEquals(4, sizes[1])
        assertEquals(4, sizes[2])
        verifyChunksCoverAllBytes(chunks, totalBytes)
    }

    // Helper method to verify chunks cover all bytes without gaps or overlaps
    private fun verifyChunksCoverAllBytes(chunks: List<LongRange>, totalBytes: Long) {
        assertTrue("First chunk should start at 0", chunks.first().first == 0L)
        assertTrue("Last chunk should end at totalBytes - 1", chunks.last().last == totalBytes - 1)
        
        val totalCovered = chunks.sumOf { it.last - it.first + 1 }
        assertEquals("Chunks should cover all bytes", totalBytes, totalCovered)
    }
}
