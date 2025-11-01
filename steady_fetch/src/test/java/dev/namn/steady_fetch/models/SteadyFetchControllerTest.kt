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

    // Tests for calculateChunkRanges

    @Test
    fun calculateChunkRanges_perfectDivision_noRemainder() {
        // Given
        val totalBytes = 100L
        val maxChunks = 4

        // When
        val chunks = steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)

        // Then
        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(4, nonNullChunks.size)
        assertEquals(0L..24L, nonNullChunks[0])
        assertEquals(25L..49L, nonNullChunks[1])
        assertEquals(50L..74L, nonNullChunks[2])
        assertEquals(75L..99L, nonNullChunks[3])
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateChunkRanges_withRemainder_remainderDistributedToFirstChunks() {
        // Given
        val totalBytes = 100L
        val maxChunks = 3 // 100 / 3 = 33 remainder 1

        // When
        val chunks = steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)

        // Then
        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(3, nonNullChunks.size)
        assertEquals(0L..33L, nonNullChunks[0]) // 34 bytes (33 + 1 remainder)
        assertEquals(34L..66L, nonNullChunks[1]) // 33 bytes
        assertEquals(67L..99L, nonNullChunks[2]) // 33 bytes
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateChunkRanges_smallFile_smallerThanMaxChunks() {
        // Given
        val totalBytes = 3L
        val maxChunks = 10

        // When
        val chunks = steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)

        // Then
        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(3, nonNullChunks.size) // Should only create 3 chunks (one per byte)
        assertEquals(0L..0L, nonNullChunks[0])
        assertEquals(1L..1L, nonNullChunks[1])
        assertEquals(2L..2L, nonNullChunks[2])
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateChunkRanges_singleByte_file() {
        // Given
        val totalBytes = 1L
        val maxChunks = 4

        // When
        val chunks = steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)

        // Then
        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(1, nonNullChunks.size)
        assertEquals(0L..0L, nonNullChunks[0])
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateChunkRanges_maxSupportedChunksLimitsResult() {
        // Given
        val totalBytes = 1000L
        val maxChunks = 20
        val maxSupportedChunks = 8

        // When
        val chunks = steadyFetchController.calculateChunkRanges(totalBytes, maxChunks, maxSupportedChunks)

        // Then
        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(8, nonNullChunks.size) // Should be limited by maxSupportedChunks
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateChunkRanges_maxChunksLimitsResult() {
        // Given
        val totalBytes = 1000L
        val maxChunks = 4
        val maxSupportedChunks = 16

        // When
        val chunks = steadyFetchController.calculateChunkRanges(totalBytes, maxChunks, maxSupportedChunks)

        // Then
        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(4, nonNullChunks.size) // Should be limited by maxChunks
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateChunkRanges_defaultMaxSupportedChunks() {
        // Given
        val totalBytes = 100L
        val maxChunks = 20

        // When
        val chunks = steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)

        // Then
        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(8, nonNullChunks.size) // Should use default maxSupportedChunks = 8
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateChunkRanges_largeFile_withManyChunks() {
        // Given
        val totalBytes = 10000L
        val maxChunks = 16
        val maxSupportedChunks = 16

        // When
        val chunks = steadyFetchController.calculateChunkRanges(totalBytes, maxChunks, maxSupportedChunks)

        // Then
        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(16, nonNullChunks.size)
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
        // Verify all chunks are approximately equal size (within 1 byte)
        val sizes = nonNullChunks.map { it.last - it.first + 1 }
        val minSize = sizes.minOrNull() ?: 0
        val maxSize = sizes.maxOrNull() ?: 0
        assertTrue("Size difference should be at most 1", maxSize - minSize <= 1)
    }

    @Test
    fun calculateChunkRanges_zeroTotalBytes_throwsException() {
        // Given
        val totalBytes = 0L
        val maxChunks = 4

        // When/Then
        try {
            steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error message should mention totalBytes", e.message?.contains("totalBytes") == true)
        }
    }

    @Test
    fun calculateChunkRanges_zeroMaxChunks_throwsException() {
        // Given
        val totalBytes = 100L
        val maxChunks = 0

        // When/Then
        try {
            steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error message should mention maxChunks", e.message?.contains("maxChunks") == true)
        }
    }

    @Test
    fun calculateChunkRanges_negativeMaxChunks_throwsException() {
        // Given
        val totalBytes = 100L
        val maxChunks = -1

        // When/Then
        try {
            steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error message should mention maxChunks", e.message?.contains("maxChunks") == true)
        }
    }

    @Test
    fun calculateChunkRanges_negativeTotalBytes_throwsException() {
        // Given
        val totalBytes = -100L
        val maxChunks = 4

        // When/Then
        try {
            steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error message should mention totalBytes", e.message?.contains("totalBytes") == true)
        }
    }

    @Test
    fun calculateChunkRanges_chunksAreContiguous_noGaps() {
        // Given
        val totalBytes = 100L
        val maxChunks = 5

        // When
        val chunks = steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)

        // Then
        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        for (i in 0 until nonNullChunks.size - 1) {
            val currentEnd = nonNullChunks[i].last
            val nextStart = nonNullChunks[i + 1].first
            assertEquals("Chunks should be contiguous", currentEnd + 1, nextStart)
        }
    }

    @Test
    fun calculateChunkRanges_chunksDoNotOverlap() {
        // Given
        val totalBytes = 100L
        val maxChunks = 5

        // When
        val chunks = steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)

        // Then
        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        for (i in nonNullChunks.indices) {
            for (j in nonNullChunks.indices) {
                if (i != j) {
                    val range1 = nonNullChunks[i]
                    val range2 = nonNullChunks[j]
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
    fun calculateChunkRanges_remainderDistribution_firstChunksGetExtraByte() {
        // Given: 13 bytes, 3 chunks = 4, 4, 5 (remainder of 1)
        val totalBytes = 13L
        val maxChunks = 3

        // When
        val chunks = steadyFetchController.calculateChunkRanges(totalBytes, maxChunks)

        // Then
        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(3, nonNullChunks.size)
        val sizes = nonNullChunks.map { it.last - it.first + 1 }
        // First chunk should get the remainder
        assertEquals(5, sizes[0]) // 13/3 = 4 remainder 1, so first gets 5
        assertEquals(4, sizes[1])
        assertEquals(4, sizes[2])
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    // Helper method to verify chunks cover all bytes without gaps or overlaps
    private fun verifyChunksCoverAllBytes(chunks: List<LongRange>, totalBytes: Long) {
        assertTrue("First chunk should start at 0", chunks.first().first == 0L)
        assertTrue("Last chunk should end at totalBytes - 1", chunks.last().last == totalBytes - 1)
        
        val totalCovered = chunks.sumOf { it.last - it.first + 1 }
        assertEquals("Chunks should cover all bytes", totalBytes, totalCovered)
    }
}
