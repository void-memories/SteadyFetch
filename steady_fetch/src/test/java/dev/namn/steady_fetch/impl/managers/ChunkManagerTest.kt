package dev.namn.steady_fetch.impl.managers

import ChunkManager

import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChunkManagerTest {

    private val chunkManager = ChunkManager()

    @Test
    fun generateChunks_respectsFileSizeAndOrder() {
        val chunks = chunkManager.generateChunks(fileName = "sample.bin", fileSize = 5 * 1024 * 1024L)

        assertTrue(chunks.isNotEmpty())
        val sorted = chunks.sortedBy { it.start }
        assertEquals(chunks, sorted)
        assertEquals(0L, chunks.first().start)
        assertEquals(5 * 1024 * 1024L - 1, chunks.last().end)
    }

    @Test
    fun generateChunks_namesAreSequential() {
        val chunks: List<DownloadChunk> = chunkManager.generateChunks("archive.tar.gz", 2_000_000L)
        val width = chunks.size.toString().length
        chunks.forEachIndexed { index, downloadChunk ->
            val expectedSuffix = ".part${(index + 1).toString().padStart(width, '0')}"
            assertTrue("Expected ${downloadChunk.name} to contain $expectedSuffix", downloadChunk.name.contains(expectedSuffix))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateChunks_throwsWhenFileSizeInvalid() {
        chunkManager.generateChunks("file.bin", 0)
    }
}

