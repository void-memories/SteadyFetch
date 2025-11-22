package dev.namn.steady_fetch.models

import android.app.Application
import dev.namn.steady_fetch.impl.uilts.Constants.DEFAULT_CHUNK_SIZE_BYTES
import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

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

    // Tests for calculateDownloadChunkRanges

    @Test
    fun calculateDownloadChunkRanges_perfectDivision_noRemainder() {
        val totalBytes = 100L
        val chunkSizeBytes = 25L

        val chunks = steadyFetchController.calculateDownloadChunkRanges(totalBytes, chunkSizeBytes)

        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(4, nonNullChunks.size)
        assertEquals(listOf(0L..24L, 25L..49L, 50L..74L, 75L..99L), nonNullChunks)
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateDownloadChunkRanges_withRemainder_lastChunkSmaller() {
        val totalBytes = 100L
        val chunkSizeBytes = 30L

        val chunks = steadyFetchController.calculateDownloadChunkRanges(totalBytes, chunkSizeBytes)

        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(4, nonNullChunks.size)
        assertEquals(listOf(0L..29L, 30L..59L, 60L..89L, 90L..99L), nonNullChunks)
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateDownloadChunkRanges_smallFile_unitChunkSize() {
        val totalBytes = 3L
        val chunkSizeBytes = 1L

        val chunks = steadyFetchController.calculateDownloadChunkRanges(totalBytes, chunkSizeBytes)

        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(listOf(0L..0L, 1L..1L, 2L..2L), nonNullChunks)
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateDownloadChunkRanges_singleByte_file() {
        val chunks = steadyFetchController.calculateDownloadChunkRanges(1L, 4L)

        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(listOf(0L..0L), nonNullChunks)
    }

    @Test
    fun calculateDownloadChunkRanges_defaultChunkSizeWhenNull() {
        val totalBytes = DEFAULT_CHUNK_SIZE_BYTES * 3

        val chunks = steadyFetchController.calculateDownloadChunkRanges(totalBytes, null)

        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(3, nonNullChunks.size)
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateDownloadChunkRanges_preferredSizeRespectedForAllButLast() {
        val totalBytes = 10_000L
        val chunkSizeBytes = 512L

        val chunks = steadyFetchController.calculateDownloadChunkRanges(totalBytes, chunkSizeBytes)

        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(20, nonNullChunks.size)
        nonNullChunks.dropLast(1).forEach { range ->
            assertEquals(512L, range.last - range.first + 1)
        }
        verifyChunksCoverAllBytes(nonNullChunks, totalBytes)
    }

    @Test
    fun calculateDownloadChunkRanges_zeroTotalBytes_throwsException() {
        val totalBytes = 0L
        val chunkSizeBytes = 1024L

        try {
            steadyFetchController.calculateDownloadChunkRanges(totalBytes, chunkSizeBytes)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error message should mention totalBytes", e.message?.contains("totalBytes") == true)
        }
    }

    @Test
    fun calculateDownloadChunkRanges_negativeTotalBytes_throwsException() {
        val totalBytes = -100L
        val chunkSizeBytes = 1024L

        try {
            steadyFetchController.calculateDownloadChunkRanges(totalBytes, chunkSizeBytes)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("Error message should mention totalBytes", e.message?.contains("totalBytes") == true)
        }
    }

    @Test
    fun calculateDownloadChunkRanges_chunksAreContiguous_noGaps() {
        val totalBytes = 100L
        val chunkSizeBytes = 20L

        val chunks = steadyFetchController.calculateDownloadChunkRanges(totalBytes, chunkSizeBytes)

        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        for (i in 0 until nonNullChunks.size - 1) {
            val currentEnd = nonNullChunks[i].last
            val nextStart = nonNullChunks[i + 1].first
            assertEquals("Chunks should be contiguous", currentEnd + 1, nextStart)
        }
    }

    @Test
    fun calculateDownloadChunkRanges_chunksDoNotOverlap() {
        val totalBytes = 100L
        val chunkSizeBytes = 20L

        val chunks = steadyFetchController.calculateDownloadChunkRanges(totalBytes, chunkSizeBytes)

        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        for (i in nonNullChunks.indices) {
            for (j in nonNullChunks.indices) {
                if (i != j) {
                    val range1 = nonNullChunks[i]
                    val range2 = nonNullChunks[j]
                    assertTrue(
                        "Ranges should not overlap: $range1 and $range2",
                        range1.last < range2.first || range2.last < range1.first
                    )
                }
            }
        }
    }

    @Test
    fun calculateDownloadChunkRanges_remainderDistribution_lastChunkSmaller() {
        val totalBytes = 13L
        val chunkSizeBytes = 4L

        val chunks = steadyFetchController.calculateDownloadChunkRanges(totalBytes, chunkSizeBytes)

        assertNotNull(chunks)
        val nonNullChunks = chunks!!
        assertEquals(listOf(0L..3L, 4L..7L, 8L..11L, 12L..12L), nonNullChunks)
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
