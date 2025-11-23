package dev.namn.steady_fetch.impl.managers

import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChunkManagerTest {

    private val chunkManager = ChunkManager()

    // ========== Input Validation Tests ==========

    @Test(expected = IllegalArgumentException::class)
    fun generateChunks_throwsWhenFileNameIsEmpty() {
        chunkManager.generateChunks("", 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateChunks_throwsWhenFileNameIsBlank() {
        chunkManager.generateChunks("   ", 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateChunks_throwsWhenFileNameIsOnlyWhitespace() {
        chunkManager.generateChunks("\t\n\r", 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateChunks_throwsWhenFileSizeIsZero() {
        chunkManager.generateChunks("file.bin", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateChunks_throwsWhenFileSizeIsNegative() {
        chunkManager.generateChunks("file.bin", -1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateChunks_throwsWhenFileSizeIsVeryNegative() {
        chunkManager.generateChunks("file.bin", Long.MIN_VALUE)
    }

    // ========== Chunk Size Determination Tests ==========

    @Test
    fun generateChunks_uses1MBChunksForSmallFiles() {
        val fileSize = 50L * 1024 * 1024 // 50MB
        val chunks = chunkManager.generateChunks("file.bin", fileSize)
        
        // Should use 1MB chunks
        val chunkSize = chunks[0].end - chunks[0].start + 1
        assertEquals(1L * 1024 * 1024, chunkSize)
    }

    @Test
    fun generateChunks_uses1MBChunksForExactly100MB() {
        val fileSize = 100L * 1024 * 1024 // Exactly 100MB
        val chunks = chunkManager.generateChunks("file.bin", fileSize)
        
        val chunkSize = chunks[0].end - chunks[0].start + 1
        assertEquals(1L * 1024 * 1024, chunkSize)
    }

    @Test
    fun generateChunks_uses4MBChunksForFilesOver100MB() {
        val fileSize = 101L * 1024 * 1024 // Just over 100MB
        val chunks = chunkManager.generateChunks("file.bin", fileSize)
        
        val chunkSize = chunks[0].end - chunks[0].start + 1
        assertEquals(4L * 1024 * 1024, chunkSize)
    }

    @Test
    fun generateChunks_uses4MBChunksForExactly1GB() {
        val fileSize = 1L * 1024 * 1024 * 1024 // Exactly 1GB
        val chunks = chunkManager.generateChunks("file.bin", fileSize)
        
        val chunkSize = chunks[0].end - chunks[0].start + 1
        assertEquals(4L * 1024 * 1024, chunkSize)
    }

    @Test
    fun generateChunks_uses8MBChunksForFilesOver1GB() {
        val fileSize = 1L * 1024 * 1024 * 1024 + 1 // Just over 1GB
        val chunks = chunkManager.generateChunks("file.bin", fileSize)
        
        val chunkSize = chunks[0].end - chunks[0].start + 1
        assertEquals(8L * 1024 * 1024, chunkSize)
    }

    @Test
    fun generateChunks_uses8MBChunksForVeryLargeFiles() {
        val fileSize = 10L * 1024 * 1024 * 1024 // 10GB
        val chunks = chunkManager.generateChunks("file.bin", fileSize)
        
        val chunkSize = chunks[0].end - chunks[0].start + 1
        assertEquals(8L * 1024 * 1024, chunkSize)
    }

    // ========== Single Chunk Tests ==========

    @Test
    fun generateChunks_createsSingleChunkFor1ByteFile() {
        val chunks = chunkManager.generateChunks("tiny.bin", 1L)
        
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].start)
        assertEquals(0L, chunks[0].end)
    }

    @Test
    fun generateChunks_createsSingleChunkForSmallFile() {
        val fileSize = 100L
        val chunks = chunkManager.generateChunks("small.bin", fileSize)
        
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].start)
        assertEquals(fileSize - 1, chunks[0].end)
    }

    @Test
    fun generateChunks_createsSingleChunkForExactly1MB() {
        val fileSize = 1L * 1024 * 1024
        val chunks = chunkManager.generateChunks("exact.bin", fileSize)
        
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].start)
        assertEquals(fileSize - 1, chunks[0].end)
    }

    @Test
    fun generateChunks_createsSingleChunkForJustUnder1MB() {
        val fileSize = 1L * 1024 * 1024 - 1
        val chunks = chunkManager.generateChunks("almost.bin", fileSize)
        
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].start)
        assertEquals(fileSize - 1, chunks[0].end)
    }

    // ========== Multiple Chunks Tests ==========

    @Test
    fun generateChunks_createsTwoChunksForJustOver1MB() {
        val fileSize = 1L * 1024 * 1024 + 1
        val chunks = chunkManager.generateChunks("split.bin", fileSize)
        
        assertEquals(2, chunks.size)
        assertEquals(0L, chunks[0].start)
        assertEquals(1L * 1024 * 1024 - 1, chunks[0].end)
        assertEquals(1L * 1024 * 1024, chunks[1].start)
        assertEquals(1L * 1024 * 1024, chunks[1].end)
    }

    @Test
    fun generateChunks_createsCorrectNumberOfChunksFor5MB() {
        val fileSize = 5L * 1024 * 1024
        val chunks = chunkManager.generateChunks("five.bin", fileSize)
        
        assertEquals(5, chunks.size)
    }

    @Test
    fun generateChunks_createsCorrectNumberOfChunksForNonDivisibleSize() {
        val fileSize = 5L * 1024 * 1024 + 1234L
        val chunks = chunkManager.generateChunks("odd.bin", fileSize)
        
        // Should create 6 chunks (5 full + 1 partial)
        assertEquals(6, chunks.size)
    }

    @Test
    fun generateChunks_lastChunkDoesNotExceedFileSize() {
        val fileSize = 5L * 1024 * 1024 + 1234L
        val chunks = chunkManager.generateChunks("odd.bin", fileSize)
        
        assertTrue(chunks.last().end < fileSize)
        assertEquals(fileSize - 1, chunks.last().end)
    }

    // ========== Chunk Continuity and Coverage Tests ==========

    @Test
    fun generateChunks_chunksAreSequential() {
        val chunks = chunkManager.generateChunks("sequential.bin", 5 * 1024 * 1024L)
        
        for (i in 1 until chunks.size) {
            assertEquals(chunks[i - 1].end + 1, chunks[i].start)
        }
    }

    @Test
    fun generateChunks_chunksCoverEntireFile() {
        val fileSize = 5L * 1024 * 1024 + 1234L
        val chunks = chunkManager.generateChunks("coverage.bin", fileSize)
        
        assertEquals(0L, chunks.first().start)
        assertEquals(fileSize - 1, chunks.last().end)
    }

    @Test
    fun generateChunks_chunksDoNotOverlap() {
        val chunks = chunkManager.generateChunks("overlap.bin", 5 * 1024 * 1024L)
        
        for (i in 1 until chunks.size) {
            assertTrue("Chunk ${i - 1} end (${chunks[i - 1].end}) should be < chunk $i start (${chunks[i].start})",
                chunks[i - 1].end < chunks[i].start)
        }
    }

    @Test
    fun generateChunks_chunksHaveNoGaps() {
        val chunks = chunkManager.generateChunks("gaps.bin", 5 * 1024 * 1024L)
        
        for (i in 1 until chunks.size) {
            assertEquals("Chunk ${i - 1} end + 1 should equal chunk $i start",
                chunks[i - 1].end + 1, chunks[i].start)
        }
    }

    @Test
    fun generateChunks_chunksAreOrderedByStart() {
        val chunks = chunkManager.generateChunks("order.bin", 5 * 1024 * 1024L)
        val sorted = chunks.sortedBy { it.start }
        
        assertEquals(chunks, sorted)
    }

    @Test
    fun generateChunks_allChunksHaveValidRanges() {
        val chunks = chunkManager.generateChunks("valid.bin", 5 * 1024 * 1024L)
        
        chunks.forEach { chunk ->
            assertTrue("Chunk start (${chunk.start}) should be <= end (${chunk.end})",
                chunk.start <= chunk.end)
            assertTrue("Chunk start should be >= 0", chunk.start >= 0)
            assertTrue("Chunk end should be >= 0", chunk.end >= 0)
        }
    }

    // ========== Chunk Naming Tests ==========

    @Test
    fun generateChunks_namesChunksWithExtension() {
        val chunks = chunkManager.generateChunks("archive.tar.gz", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertTrue("Chunk name should contain original extension: ${chunk.name}",
                chunk.name.contains(".tar.gz"))
            assertTrue("Chunk name should contain .part: ${chunk.name}",
                chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_namesChunksWithoutExtension() {
        val chunks = chunkManager.generateChunks("README", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertTrue("Chunk name should contain .part: ${chunk.name}",
                chunk.name.contains(".part"))
            assertFalse("Chunk name should not contain extension: ${chunk.name}",
                chunk.name.matches(Regex(".*\\.[a-zA-Z]+\\..*")))
        }
    }

    @Test
    fun generateChunks_namesChunksWithMultipleDots() {
        val chunks = chunkManager.generateChunks("file.name.with.dots.txt", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertTrue("Chunk name should contain .txt: ${chunk.name}",
                chunk.name.contains(".txt"))
            assertTrue("Chunk name should contain .part: ${chunk.name}",
                chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_namesChunksForHiddenFile() {
        val chunks = chunkManager.generateChunks(".gitignore", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertTrue("Chunk name should start with .: ${chunk.name}",
                chunk.name.startsWith("."))
            assertTrue("Chunk name should contain .part: ${chunk.name}",
                chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_namesChunksForFileEndingWithDot() {
        val chunks = chunkManager.generateChunks("file.", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertTrue("Chunk name should contain .part: ${chunk.name}",
                chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_namesChunksForFileWithOnlyExtension() {
        val chunks = chunkManager.generateChunks(".gitignore", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertNotNull("Chunk name should not be null", chunk.name)
            assertTrue("Chunk name should contain .part: ${chunk.name}",
                chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_chunkNumbersAre1Indexed() {
        val chunks = chunkManager.generateChunks("file.bin", 2_000_000L)
        
        chunks.forEachIndexed { index, chunk ->
            val chunkNumber = index + 1
            assertTrue("Chunk name should contain part number: ${chunk.name}",
                chunk.name.contains("part${chunkNumber.toString().padStart(chunks.size.toString().length, '0')}"))
        }
    }

    @Test
    fun generateChunks_chunkNumbersArePaddedCorrectly() {
        val chunks = chunkManager.generateChunks("file.bin", 200_000_000L) // Creates many chunks
        
        val width = chunks.size.toString().length
        chunks.forEachIndexed { index, chunk ->
            val chunkNumber = index + 1
            val paddedNumber = chunkNumber.toString().padStart(width, '0')
            assertTrue("Chunk name should contain padded number: ${chunk.name}",
                chunk.name.contains("part$paddedNumber"))
        }
    }

    @Test
    fun generateChunks_singleChunkHasCorrectPadding() {
        val chunks = chunkManager.generateChunks("file.bin", 500_000L) // Single chunk
        
        assertEquals(1, chunks.size)
        // For single chunk, width=1, so no padding needed (part1, not part01)
        assertTrue("Single chunk should have .part1: ${chunks[0].name}",
            chunks[0].name.contains("part1"))
    }

    @Test
    fun generateChunks_chunkNamesAreUnique() {
        val chunks = chunkManager.generateChunks("file.bin", 5 * 1024 * 1024L)
        
        val names = chunks.map { it.name }.toSet()
        assertEquals("All chunk names should be unique", chunks.size, names.size)
    }

    @Test
    fun generateChunks_chunkNamesContainOriginalFileName() {
        val fileName = "my-special-file.bin"
        val chunks = chunkManager.generateChunks(fileName, 2_000_000L)
        
        chunks.forEach { chunk ->
            assertTrue("Chunk name should contain base filename: ${chunk.name}",
                chunk.name.contains("my-special-file"))
        }
    }

    // ========== Boundary Condition Tests ==========

    @Test
    fun generateChunks_handlesFileSizeAtChunkSizeBoundary() {
        val chunkSize = 1L * 1024 * 1024
        val fileSize = chunkSize
        val chunks = chunkManager.generateChunks("boundary.bin", fileSize)
        
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].start)
        assertEquals(fileSize - 1, chunks[0].end)
    }

    @Test
    fun generateChunks_handlesFileSizeJustBelowChunkSize() {
        val chunkSize = 1L * 1024 * 1024
        val fileSize = chunkSize - 1
        val chunks = chunkManager.generateChunks("below.bin", fileSize)
        
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].start)
        assertEquals(fileSize - 1, chunks[0].end)
    }

    @Test
    fun generateChunks_handlesFileSizeJustAboveChunkSize() {
        val chunkSize = 1L * 1024 * 1024
        val fileSize = chunkSize + 1
        val chunks = chunkManager.generateChunks("above.bin", fileSize)
        
        assertEquals(2, chunks.size)
        assertEquals(0L, chunks[0].start)
        assertEquals(chunkSize - 1, chunks[0].end)
        assertEquals(chunkSize, chunks[1].start)
        assertEquals(chunkSize, chunks[1].end)
    }

    @Test
    fun generateChunks_handlesVeryLargeFileSize() {
        // Use a large but manageable size to avoid OOM (100GB)
        val fileSize = 100L * 1024 * 1024 * 1024
        val chunks = chunkManager.generateChunks("huge.bin", fileSize)
        
        assertTrue("Should create chunks for very large file", chunks.isNotEmpty())
        assertEquals(0L, chunks.first().start)
        assertEquals(fileSize - 1, chunks.last().end)
        
        // Should use 8MB chunks for files over 1GB
        val chunkSize = chunks[0].end - chunks[0].start + 1
        assertEquals(8L * 1024 * 1024, chunkSize)
    }

    @Test
    fun generateChunks_handlesFileSizeNear100MBBoundary() {
        val boundary = 100L * 1024 * 1024
        
        val chunksJustBelow = chunkManager.generateChunks("below100.bin", boundary - 1)
        val chunkSizeBelow = chunksJustBelow[0].end - chunksJustBelow[0].start + 1
        assertEquals(1L * 1024 * 1024, chunkSizeBelow)
        
        val chunksJustAbove = chunkManager.generateChunks("above100.bin", boundary + 1)
        val chunkSizeAbove = chunksJustAbove[0].end - chunksJustAbove[0].start + 1
        assertEquals(4L * 1024 * 1024, chunkSizeAbove)
    }

    @Test
    fun generateChunks_handlesFileSizeNear1GBBoundary() {
        val boundary = 1L * 1024 * 1024 * 1024
        
        val chunksJustBelow = chunkManager.generateChunks("below1gb.bin", boundary - 1)
        val chunkSizeBelow = chunksJustBelow[0].end - chunksJustBelow[0].start + 1
        assertEquals(4L * 1024 * 1024, chunkSizeBelow)
        
        val chunksJustAbove = chunkManager.generateChunks("above1gb.bin", boundary + 1)
        val chunkSizeAbove = chunksJustAbove[0].end - chunksJustAbove[0].start + 1
        assertEquals(8L * 1024 * 1024, chunkSizeAbove)
    }

    // ========== Special File Name Tests ==========

    @Test
    fun generateChunks_handlesFileNameWithSpecialCharacters() {
        val chunks = chunkManager.generateChunks("file-name_with.special@chars#123.bin", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertNotNull("Chunk name should not be null", chunk.name)
            assertTrue("Chunk name should contain .part", chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_handlesFileNameWithUnicode() {
        val chunks = chunkManager.generateChunks("файл.txt", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertNotNull("Chunk name should not be null", chunk.name)
            assertTrue("Chunk name should contain .part", chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_handlesFileNameWithSpaces() {
        val chunks = chunkManager.generateChunks("my file name.txt", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertNotNull("Chunk name should not be null", chunk.name)
            assertTrue("Chunk name should contain .part", chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_handlesVeryLongFileName() {
        val longName = "a".repeat(200) + ".txt"
        val chunks = chunkManager.generateChunks(longName, 2_000_000L)
        
        chunks.forEach { chunk ->
            assertNotNull("Chunk name should not be null", chunk.name)
            assertTrue("Chunk name should contain .part", chunk.name.contains(".part"))
        }
    }

    // ========== Chunk Size Consistency Tests ==========

    @Test
    fun generateChunks_allChunksExceptLastHaveSameSize() {
        val chunks = chunkManager.generateChunks("consistent.bin", 5L * 1024 * 1024 + 1000L)
        
        if (chunks.size > 1) {
            val expectedChunkSize = chunks[0].end - chunks[0].start + 1
            
            for (i in 0 until chunks.size - 1) {
                val chunkSize = chunks[i].end - chunks[i].start + 1
                assertEquals("Chunk $i should have consistent size", expectedChunkSize, chunkSize)
            }
            
            // Last chunk may be smaller
            val lastChunkSize = chunks.last().end - chunks.last().start + 1
            assertTrue("Last chunk should be <= regular chunk size",
                lastChunkSize <= expectedChunkSize)
        }
    }

    @Test
    fun generateChunks_chunkSizesMatchDeterminedChunkSize() {
        val fileSize = 5L * 1024 * 1024
        val chunks = chunkManager.generateChunks("match.bin", fileSize)
        
        val expectedChunkSize = 1L * 1024 * 1024 // For files <= 100MB
        val actualChunkSize = chunks[0].end - chunks[0].start + 1
        
        assertEquals("Chunk size should match determined size", expectedChunkSize, actualChunkSize)
    }

    // ========== Comprehensive Integration Tests ==========

    @Test
    fun generateChunks_comprehensiveTestForLargeFile() {
        val fileSize = 500L * 1024 * 1024 // 500MB
        val chunks = chunkManager.generateChunks("large-file.iso", fileSize)
        
        // Should use 4MB chunks (between 100MB and 1GB)
        val expectedChunkSize = 4L * 1024 * 1024
        val actualChunkSize = chunks[0].end - chunks[0].start + 1
        assertEquals(expectedChunkSize, actualChunkSize)
        
        // Verify chunk count
        val expectedChunks = kotlin.math.ceil(fileSize / expectedChunkSize.toDouble()).toLong()
        assertEquals(expectedChunks.toInt(), chunks.size)
        
        // Verify coverage
        assertEquals(0L, chunks.first().start)
        assertEquals(fileSize - 1, chunks.last().end)
        
        // Verify continuity
        for (i in 1 until chunks.size) {
            assertEquals(chunks[i - 1].end + 1, chunks[i].start)
        }
        
        // Verify naming
        chunks.forEachIndexed { index, chunk ->
            assertTrue(chunk.name.contains(".iso"))
            assertTrue(chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_comprehensiveTestForSmallFile() {
        val fileSize = 500L // 500 bytes
        val chunks = chunkManager.generateChunks("small.txt", fileSize)
        
        // Should determine 1MB chunk size, but actual chunk only covers file size
        val determinedChunkSize = 1L * 1024 * 1024
        // The actual chunk size will be the file size since it's smaller than determined chunk size
        val actualChunkSize = chunks[0].end - chunks[0].start + 1
        assertEquals(fileSize, actualChunkSize)
        
        // Should create only 1 chunk
        assertEquals(1, chunks.size)
        
        // Verify coverage
        assertEquals(0L, chunks[0].start)
        assertEquals(fileSize - 1, chunks[0].end)
        
        // Verify naming
        assertTrue(chunks[0].name.contains(".txt"))
        // For single chunk, width=1, so part1 not part01
        assertTrue(chunks[0].name.contains("part1"))
    }

    @Test
    fun generateChunks_comprehensiveTestForGiantFile() {
        val fileSize = 5L * 1024 * 1024 * 1024 // 5GB
        val chunks = chunkManager.generateChunks("giant.bin", fileSize)
        
        // Should use 8MB chunks (over 1GB)
        val expectedChunkSize = 8L * 1024 * 1024
        val actualChunkSize = chunks[0].end - chunks[0].start + 1
        assertEquals(expectedChunkSize, actualChunkSize)
        
        // Verify chunk count
        val expectedChunks = kotlin.math.ceil(fileSize / expectedChunkSize.toDouble()).toLong()
        assertEquals(expectedChunks.toInt(), chunks.size)
        
        // Verify all properties
        assertEquals(0L, chunks.first().start)
        assertEquals(fileSize - 1, chunks.last().end)
        
        for (i in 1 until chunks.size) {
            assertEquals(chunks[i - 1].end + 1, chunks[i].start)
        }
    }

    // ========== Edge Cases for Chunk Naming ==========

    @Test
    fun generateChunks_handlesFileNameWithNoExtension() {
        val chunks = chunkManager.generateChunks("README", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertTrue("Chunk name should contain README: ${chunk.name}",
                chunk.name.contains("README"))
            assertTrue("Chunk name should contain .part: ${chunk.name}",
                chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_handlesFileNameWithLeadingDot() {
        val chunks = chunkManager.generateChunks(".htaccess", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertTrue("Chunk name should start with .: ${chunk.name}",
                chunk.name.startsWith("."))
            assertTrue("Chunk name should contain .part: ${chunk.name}",
                chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_handlesFileNameWithTrailingDot() {
        val chunks = chunkManager.generateChunks("file.", 2_000_000L)
        
        chunks.forEach { chunk ->
            assertTrue("Chunk name should contain file: ${chunk.name}",
                chunk.name.contains("file"))
            assertTrue("Chunk name should contain .part: ${chunk.name}",
                chunk.name.contains(".part"))
        }
    }

    @Test
    fun generateChunks_handlesFileNameWithMultipleExtensions() {
        val chunks = chunkManager.generateChunks("archive.tar.gz", 2_000_000L)
        
        chunks.forEach { chunk ->
            // Should preserve .tar.gz extension
            assertTrue("Chunk name should contain .tar.gz: ${chunk.name}",
                chunk.name.contains(".tar.gz"))
            assertTrue("Chunk name should contain .part: ${chunk.name}",
                chunk.name.contains(".part"))
        }
    }

    // ========== Mathematical Edge Cases ==========

    @Test
    fun generateChunks_handlesFileSizeThatDividesEvenly() {
        val fileSize = 4L * 1024 * 1024 // Exactly 4 chunks of 1MB
        val chunks = chunkManager.generateChunks("even.bin", fileSize)
        
        assertEquals(4, chunks.size)
        chunks.forEach { chunk ->
            val chunkSize = chunk.end - chunk.start + 1
            assertEquals(1L * 1024 * 1024, chunkSize)
        }
    }

    @Test
    fun generateChunks_handlesFileSizeWithRemainder() {
        val fileSize = 4L * 1024 * 1024 + 500L // 4 chunks + remainder
        val chunks = chunkManager.generateChunks("remainder.bin", fileSize)
        
        assertEquals(5, chunks.size)
        
        // First 4 chunks should be full size
        for (i in 0 until 4) {
            val chunkSize = chunks[i].end - chunks[i].start + 1
            assertEquals(1L * 1024 * 1024, chunkSize)
        }
        
        // Last chunk should be smaller
        val lastChunkSize = chunks.last().end - chunks.last().start + 1
        assertEquals(500L, lastChunkSize)
    }

    @Test
    fun generateChunks_handlesVerySmallRemainder() {
        val fileSize = 2L * 1024 * 1024 + 1L // Just 1 byte remainder
        val chunks = chunkManager.generateChunks("tiny-remainder.bin", fileSize)
        
        assertEquals(3, chunks.size)
        assertEquals(1L, chunks.last().end - chunks.last().start + 1)
    }

    // ========== Chunk Index and Numbering Tests ==========

    @Test
    fun generateChunks_chunkNumbersStartAt1() {
        val chunks = chunkManager.generateChunks("numbered.bin", 2_000_000L)
        
        val firstChunkNumber = extractChunkNumber(chunks[0].name)
        assertEquals(1, firstChunkNumber)
    }

    @Test
    fun generateChunks_chunkNumbersAreSequential() {
        val chunks = chunkManager.generateChunks("sequential.bin", 5 * 1024 * 1024L)
        
        chunks.forEachIndexed { index, chunk ->
            val chunkNumber = extractChunkNumber(chunk.name)
            assertEquals(index + 1, chunkNumber)
        }
    }

    @Test
    fun generateChunks_chunkNumbersMatchTotalChunks() {
        val chunks = chunkManager.generateChunks("total.bin", 5 * 1024 * 1024L)
        
        val lastChunkNumber = extractChunkNumber(chunks.last().name)
        assertEquals(chunks.size, lastChunkNumber)
    }

    // Helper function to extract chunk number from name
    private fun extractChunkNumber(chunkName: String): Int {
        val regex = Regex("part(\\d+)")
        val match = regex.find(chunkName)
        return match?.groupValues?.get(1)?.toInt() ?: -1
    }
}




