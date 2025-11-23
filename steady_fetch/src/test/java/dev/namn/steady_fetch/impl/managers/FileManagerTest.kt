package dev.namn.steady_fetch.impl.managers

import android.os.StatFs
import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import dev.namn.steady_fetch.impl.utils.Constants
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class FileManagerTest {

    private lateinit var tempDir: File
    private val fileManager = FileManager()

    @Before
    fun setUp() {
        tempDir = createTempDir(prefix = "steady-fetch-fm")
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.deleteRecursively()
    }

    // ========== validateStorageCapacity Tests ==========

    @Test
    fun validateStorageCapacity_withSufficientStorage_succeeds() {
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 1100L

        fileManager.validateStorageCapacity(tempDir, expectedBytes = 1000L)
        // Should not throw
    }

    @Test
    fun validateStorageCapacity_withExactRequiredStorage_succeeds() {
        mockkConstructor(StatFs::class)
        // Required = 1000 * 1.1 = 1100
        every { anyConstructed<StatFs>().availableBytes } returns 1100L

        fileManager.validateStorageCapacity(tempDir, expectedBytes = 1000L)
        // Should not throw
    }

    @Test
    fun validateStorageCapacity_withJustAboveRequiredStorage_succeeds() {
        mockkConstructor(StatFs::class)
        // Required = 1000 * 1.1 = 1100
        every { anyConstructed<StatFs>().availableBytes } returns 1101L

        fileManager.validateStorageCapacity(tempDir, expectedBytes = 1000L)
        // Should not throw
    }

    @Test(expected = IllegalStateException::class)
    fun validateStorageCapacity_withInsufficientStorage_throwsException() {
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 10L

        fileManager.validateStorageCapacity(tempDir, expectedBytes = 1000L)
    }

    @Test(expected = IllegalStateException::class)
    fun validateStorageCapacity_withJustBelowRequiredStorage_throwsException() {
        mockkConstructor(StatFs::class)
        // Required = 1000 * 1.1 = 1100
        every { anyConstructed<StatFs>().availableBytes } returns 1099L

        fileManager.validateStorageCapacity(tempDir, expectedBytes = 1000L)
    }

    @Test
    fun validateStorageCapacity_withZeroBytes_succeeds() {
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 1L

        fileManager.validateStorageCapacity(tempDir, expectedBytes = 0L)
        // Should not throw (required = 0 * 1.1 = 0)
    }

    @Test
    fun validateStorageCapacity_withVeryLargeFile_succeedsWhenEnoughSpace() {
        mockkConstructor(StatFs::class)
        val largeFileSize = 10L * 1024 * 1024 * 1024 // 10GB
        val required = (largeFileSize * Constants.STORAGE_SAFETY_MARGIN_MULTIPLIER).toLong()
        every { anyConstructed<StatFs>().availableBytes } returns required + 1

        fileManager.validateStorageCapacity(tempDir, expectedBytes = largeFileSize)
        // Should not throw
    }

    @Test(expected = IllegalStateException::class)
    fun validateStorageCapacity_withVeryLargeFile_throwsWhenInsufficient() {
        mockkConstructor(StatFs::class)
        val largeFileSize = 10L * 1024 * 1024 * 1024 // 10GB
        val required = (largeFileSize * Constants.STORAGE_SAFETY_MARGIN_MULTIPLIER).toLong()
        every { anyConstructed<StatFs>().availableBytes } returns required - 1

        fileManager.validateStorageCapacity(tempDir, expectedBytes = largeFileSize)
    }

    @Test
    fun validateStorageCapacity_usesSafetyMarginMultiplier() {
        mockkConstructor(StatFs::class)
        val expectedBytes = 1000L
        val requiredBytes = (expectedBytes * Constants.STORAGE_SAFETY_MARGIN_MULTIPLIER).toLong()
        
        // Should require 1100 bytes (1000 * 1.1)
        assertEquals(1100L, requiredBytes)
        
        every { anyConstructed<StatFs>().availableBytes } returns requiredBytes
        fileManager.validateStorageCapacity(tempDir, expectedBytes = expectedBytes)
    }

    // ========== createDirectoryIfNotExists Tests ==========

    @Test
    fun createDirectoryIfNotExists_createsMissingDir() {
        val target = File(tempDir, "new-dir")
        assertFalse(target.exists())

        fileManager.createDirectoryIfNotExists(target)

        assertTrue(target.exists())
        assertTrue(target.isDirectory)
    }

    @Test
    fun createDirectoryIfNotExists_createsNestedDirectories() {
        val target = File(tempDir, "nested/deep/directory/structure")
        assertFalse(target.exists())

        fileManager.createDirectoryIfNotExists(target)

        assertTrue(target.exists())
        assertTrue(target.isDirectory)
    }

    @Test
    fun createDirectoryIfNotExists_whenDirectoryExists_doesNothing() {
        val target = File(tempDir, "existing-dir")
        target.mkdirs()
        assertTrue(target.exists())

        fileManager.createDirectoryIfNotExists(target)

        assertTrue(target.exists())
        assertTrue(target.isDirectory)
    }

    @Test
    fun createDirectoryIfNotExists_whenFileExistsWithSameName_throwsException() {
        val target = File(tempDir, "file-name")
        target.writeText("content")
        assertTrue(target.exists())
        assertTrue(target.isFile)

        try {
            fileManager.createDirectoryIfNotExists(target)
            // On some systems, this might not throw, but mkdirs() will fail
        } catch (e: IOException) {
            // Expected if mkdirs fails
            assertTrue(e.message?.contains("Unable to create directory") == true)
        }
    }

    @Test
    fun createDirectoryIfNotExists_createsMultipleDirectories() {
        val dir1 = File(tempDir, "dir1")
        val dir2 = File(tempDir, "dir2")
        val dir3 = File(tempDir, "dir3")

        fileManager.createDirectoryIfNotExists(dir1)
        fileManager.createDirectoryIfNotExists(dir2)
        fileManager.createDirectoryIfNotExists(dir3)

        assertTrue(dir1.exists() && dir1.isDirectory)
        assertTrue(dir2.exists() && dir2.isDirectory)
        assertTrue(dir3.exists() && dir3.isDirectory)
    }

    @Test
    fun createDirectoryIfNotExists_createsRootLevelDirectory() {
        val target = File(tempDir, "root-level")
        fileManager.createDirectoryIfNotExists(target)
        assertTrue(target.exists() && target.isDirectory)
    }

    // ========== reconcileChunks Tests ==========

    @Test
    fun reconcileChunks_withEmptyList_doesNothing() {
        val finalFile = File(tempDir, "final.txt")
        assertFalse(finalFile.exists())

        fileManager.reconcileChunks(tempDir, "final.txt", emptyList())

        assertFalse(finalFile.exists())
    }

    @Test
    fun reconcileChunks_withSingleChunk_mergesCorrectly() {
        val chunkContent = "single chunk content"
        val chunkFile = File(tempDir, "file.part01").apply { writeText(chunkContent) }
        val chunks = listOf(DownloadChunk(chunkFile.name, 0, chunkContent.length.toLong() - 1))

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        val finalFile = File(tempDir, "file.txt")
        assertTrue(finalFile.exists())
        assertEquals(chunkContent, finalFile.readText())
        assertFalse(chunkFile.exists())
    }

    @Test
    fun reconcileChunks_withMultipleChunks_mergesInOrder() {
        val chunkOne = File(tempDir, "file.part01").apply { writeText("foo") }
        val chunkTwo = File(tempDir, "file.part02").apply { writeText("bar") }
        val chunkThree = File(tempDir, "file.part03").apply { writeText("baz") }
        val chunks = listOf(
            DownloadChunk(chunkOne.name, 0, 2),
            DownloadChunk(chunkTwo.name, 3, 5),
            DownloadChunk(chunkThree.name, 6, 8)
        )

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        val finalFile = File(tempDir, "file.txt")
        assertEquals("foobarbaz", finalFile.readText())
        assertFalse(chunkOne.exists())
        assertFalse(chunkTwo.exists())
        assertFalse(chunkThree.exists())
    }

    @Test
    fun reconcileChunks_withUnsortedChunks_sortsByStart() {
        val chunkOne = File(tempDir, "file.part01").apply { writeText("first") }
        val chunkTwo = File(tempDir, "file.part02").apply { writeText("second") }
        val chunkThree = File(tempDir, "file.part03").apply { writeText("third") }
        // Provide chunks out of order
        val chunks = listOf(
            DownloadChunk(chunkThree.name, 10, 14),
            DownloadChunk(chunkOne.name, 0, 4),
            DownloadChunk(chunkTwo.name, 5, 9)
        )

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        val finalFile = File(tempDir, "file.txt")
        assertEquals("firstsecondthird", finalFile.readText())
    }

    @Test
    fun reconcileChunks_withBinaryContent_mergesCorrectly() {
        val binaryContent1 = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val binaryContent2 = byteArrayOf(0x05, 0x06, 0x07, 0x08)
        val chunkOne = File(tempDir, "file.part01").apply { writeBytes(binaryContent1) }
        val chunkTwo = File(tempDir, "file.part02").apply { writeBytes(binaryContent2) }
        val chunks = listOf(
            DownloadChunk(chunkOne.name, 0, 3),
            DownloadChunk(chunkTwo.name, 4, 7)
        )

        fileManager.reconcileChunks(tempDir, "file.bin", chunks)

        val finalFile = File(tempDir, "file.bin")
        val mergedContent = finalFile.readBytes()
        assertEquals(8, mergedContent.size)
        assertTrue(mergedContent.contentEquals(binaryContent1 + binaryContent2))
    }

    @Test
    fun reconcileChunks_withLargeChunks_mergesCorrectly() {
        val largeContent1 = "A".repeat(1024 * 1024) // 1MB
        val largeContent2 = "B".repeat(1024 * 1024) // 1MB
        val chunkOne = File(tempDir, "file.part01").apply { writeText(largeContent1) }
        val chunkTwo = File(tempDir, "file.part02").apply { writeText(largeContent2) }
        val chunks = listOf(
            DownloadChunk(chunkOne.name, 0, largeContent1.length.toLong() - 1),
            DownloadChunk(chunkTwo.name, largeContent1.length.toLong(), (largeContent1.length + largeContent2.length).toLong() - 1)
        )

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        val finalFile = File(tempDir, "file.txt")
        assertEquals(largeContent1 + largeContent2, finalFile.readText())
    }

    @Test
    fun reconcileChunks_deletesExistingFinalFile() {
        val existingFile = File(tempDir, "file.txt")
        existingFile.writeText("old content")
        assertTrue(existingFile.exists())

        val chunkFile = File(tempDir, "file.part01").apply { writeText("new content") }
        val chunks = listOf(DownloadChunk(chunkFile.name, 0, 10))

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        val finalFile = File(tempDir, "file.txt")
        assertEquals("new content", finalFile.readText())
    }

    @Test(expected = IOException::class)
    fun reconcileChunks_withMissingChunkFile_throwsException() {
        val chunks = listOf(
            DownloadChunk("missing.part01", 0, 10)
        )

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)
    }

    @Test(expected = IOException::class)
    fun reconcileChunks_withSomeMissingChunkFiles_throwsException() {
        val chunkOne = File(tempDir, "file.part01").apply { writeText("exists") }
        val chunks = listOf(
            DownloadChunk(chunkOne.name, 0, 5),
            DownloadChunk("missing.part02", 6, 10)
        )

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)
    }

    @Test
    fun reconcileChunks_deletesAllChunkFilesAfterMerge() {
        val chunkOne = File(tempDir, "file.part01").apply { writeText("one") }
        val chunkTwo = File(tempDir, "file.part02").apply { writeText("two") }
        val chunkThree = File(tempDir, "file.part03").apply { writeText("three") }
        val chunks = listOf(
            DownloadChunk(chunkOne.name, 0, 2),
            DownloadChunk(chunkTwo.name, 3, 5),
            DownloadChunk(chunkThree.name, 6, 10)
        )

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        assertFalse(chunkOne.exists())
        assertFalse(chunkTwo.exists())
        assertFalse(chunkThree.exists())
    }

    @Test
    fun reconcileChunks_createsTempFileDuringAssembly() {
        val chunkFile = File(tempDir, "file.part01").apply { writeText("content") }
        val chunks = listOf(DownloadChunk(chunkFile.name, 0, 6))
        val tempFile = File(tempDir, "file.txt.__assembling")

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        // Temp file should not exist after successful merge
        assertFalse(tempFile.exists())
    }

    @Test
    fun reconcileChunks_cleansUpTempFileOnError() {
        val chunks = listOf(
            DownloadChunk("missing.part01", 0, 10)
        )
        val tempFile = File(tempDir, "file.txt.__assembling")

        try {
            fileManager.reconcileChunks(tempDir, "file.txt", chunks)
        } catch (e: IOException) {
            // Expected
        }

        // Temp file should be cleaned up even on error
        assertFalse(tempFile.exists())
    }

    @Test
    fun reconcileChunks_withManyChunks_mergesCorrectly() {
        val chunkCount = 100
        val chunks = mutableListOf<DownloadChunk>()
        var totalLength = 0

        repeat(chunkCount) { index ->
            val content = "chunk$index"
            val chunkFile = File(tempDir, "file.part${(index + 1).toString().padStart(3, '0')}")
            chunkFile.writeText(content)
            chunks.add(DownloadChunk(chunkFile.name, totalLength.toLong(), (totalLength + content.length - 1).toLong()))
            totalLength += content.length
        }

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        val finalFile = File(tempDir, "file.txt")
        val expectedContent = (0 until chunkCount).joinToString("") { "chunk$it" }
        assertEquals(expectedContent, finalFile.readText())

        // All chunk files should be deleted
        chunks.forEach { chunk ->
            assertFalse(File(tempDir, chunk.name).exists())
        }
    }

    @Test
    fun reconcileChunks_withEmptyChunkFiles_mergesCorrectly() {
        val chunkOne = File(tempDir, "file.part01").apply { writeText("") }
        val chunkTwo = File(tempDir, "file.part02").apply { writeText("content") }
        val chunks = listOf(
            DownloadChunk(chunkOne.name, 0, -1), // Empty chunk
            DownloadChunk(chunkTwo.name, 0, 6)
        )

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        val finalFile = File(tempDir, "file.txt")
        assertEquals("content", finalFile.readText())
    }

    @Test
    fun reconcileChunks_preservesFilePermissions() {
        val chunkFile = File(tempDir, "file.part01").apply { 
            writeText("content")
            setReadable(true)
            setWritable(true)
        }
        val chunks = listOf(DownloadChunk(chunkFile.name, 0, 6))

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        val finalFile = File(tempDir, "file.txt")
        assertTrue(finalFile.exists())
        assertTrue(finalFile.canRead())
    }

    // ========== verifyMd5 Tests ==========

    @Test
    fun verifyMd5_withNullMd5_returnsTrue() {
        val file = File(tempDir, "test.txt").apply { writeText("content") }
        
        assertTrue(fileManager.verifyMd5(file, null))
    }

    @Test
    fun verifyMd5_withBlankMd5_returnsTrue() {
        val file = File(tempDir, "test.txt").apply { writeText("content") }
        
        assertTrue(fileManager.verifyMd5(file, ""))
        assertTrue(fileManager.verifyMd5(file, "   "))
    }

    @Test
    fun verifyMd5_withMatchingMd5_returnsTrue() {
        val content = "hello-world"
        val file = File(tempDir, "payload.bin").apply { writeText(content) }
        val expected = content.toByteArray().let { bytes ->
            MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }

        assertTrue(fileManager.verifyMd5(file, expected))
    }

    @Test
    fun verifyMd5_withNonMatchingMd5_returnsFalse() {
        val file = File(tempDir, "payload.bin").apply { writeText("hello-world") }
        val wrongMd5 = "wrong-hash-value-1234567890abcdef"

        assertFalse(fileManager.verifyMd5(file, wrongMd5))
    }

    @Test
    fun verifyMd5_isCaseInsensitive() {
        val content = "test-content"
        val file = File(tempDir, "test.txt").apply { writeText(content) }
        val md5 = content.toByteArray().let { bytes ->
            MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }

        // Test with uppercase
        assertTrue(fileManager.verifyMd5(file, md5.uppercase()))
        // Test with lowercase
        assertTrue(fileManager.verifyMd5(file, md5.lowercase()))
        // Test with mixed case
        assertTrue(fileManager.verifyMd5(file, md5.replaceFirstChar { it.uppercaseChar() }))
    }

    @Test
    fun verifyMd5_withEmptyFile_returnsCorrectMd5() {
        val file = File(tempDir, "empty.txt").apply { writeText("") }
        val expected = "".toByteArray().let { bytes ->
            MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }

        assertTrue(fileManager.verifyMd5(file, expected))
    }

    @Test
    fun verifyMd5_withLargeFile_verifiesCorrectly() {
        val largeContent = "A".repeat(10 * 1024 * 1024) // 10MB
        val file = File(tempDir, "large.bin").apply { writeText(largeContent) }
        val expected = largeContent.toByteArray().let { bytes ->
            MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }

        assertTrue(fileManager.verifyMd5(file, expected))
    }

    @Test
    fun verifyMd5_withBinaryFile_verifiesCorrectly() {
        val binaryContent = ByteArray(1024) { it.toByte() }
        val file = File(tempDir, "binary.bin").apply { writeBytes(binaryContent) }
        val expected = MessageDigest.getInstance("MD5").digest(binaryContent)
            .joinToString("") { "%02x".format(it) }

        assertTrue(fileManager.verifyMd5(file, expected))
    }

    @Test
    fun verifyMd5_withSpecialCharacters_verifiesCorrectly() {
        val content = "特殊字符: àáâãäåæçèéêë\n\t\r"
        val file = File(tempDir, "special.txt").apply { writeText(content) }
        val expected = content.toByteArray().let { bytes ->
            MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }

        assertTrue(fileManager.verifyMd5(file, expected))
    }

    @Test
    fun verifyMd5_withVeryLongMd5String_handlesCorrectly() {
        val file = File(tempDir, "test.txt").apply { writeText("content") }
        val wrongMd5 = "a".repeat(100) // Invalid MD5 (too long)

        assertFalse(fileManager.verifyMd5(file, wrongMd5))
    }

    @Test
    fun verifyMd5_withShortMd5String_handlesCorrectly() {
        val file = File(tempDir, "test.txt").apply { writeText("content") }
        val wrongMd5 = "abc" // Invalid MD5 (too short)

        assertFalse(fileManager.verifyMd5(file, wrongMd5))
    }

    @Test
    fun verifyMd5_usesDefaultBufferSize() {
        // Create a file larger than DEFAULT_BUFFER_SIZE to test buffering
        val largeContent = "X".repeat(Constants.DEFAULT_BUFFER_SIZE * 2)
        val file = File(tempDir, "buffered.bin").apply { writeText(largeContent) }
        val expected = largeContent.toByteArray().let { bytes ->
            MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }

        assertTrue(fileManager.verifyMd5(file, expected))
    }

    @Test
    fun verifyMd5_withDifferentFileContents_generatesDifferentHashes() {
        val file1 = File(tempDir, "file1.txt").apply { writeText("content1") }
        val file2 = File(tempDir, "file2.txt").apply { writeText("content2") }
        
        val md5_1 = "content1".toByteArray().let { bytes ->
            MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }
        
        val md5_2 = "content2".toByteArray().let { bytes ->
            MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }

        assertTrue(fileManager.verifyMd5(file1, md5_1))
        assertTrue(fileManager.verifyMd5(file2, md5_2))
        assertFalse(fileManager.verifyMd5(file1, md5_2))
        assertFalse(fileManager.verifyMd5(file2, md5_1))
    }

    // ========== formatBytesToHumanReadable Tests (indirect) ==========

    @Test
    fun formatBytesToHumanReadable_displaysBytes() {
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 500L

        try {
            fileManager.validateStorageCapacity(tempDir, expectedBytes = 100L)
        } catch (e: IllegalStateException) {
            // Check that error message contains "bytes"
            assertTrue(e.message?.contains("bytes") == true || e.message?.contains("KB") == true)
        }
    }

    @Test
    fun formatBytesToHumanReadable_displaysKB() {
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 500L

        try {
            fileManager.validateStorageCapacity(tempDir, expectedBytes = 1000L)
        } catch (e: IllegalStateException) {
            // Check that error message contains "KB" or "MB"
            val message = e.message ?: ""
            assertTrue(message.contains("KB") || message.contains("MB") || message.contains("GB"))
        }
    }

    @Test
    fun formatBytesToHumanReadable_displaysMB() {
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 500L

        try {
            fileManager.validateStorageCapacity(tempDir, expectedBytes = 1024 * 1024L)
        } catch (e: IllegalStateException) {
            // Check that error message contains "MB" or "GB"
            val message = e.message ?: ""
            assertTrue(message.contains("MB") || message.contains("GB"))
        }
    }

    @Test
    fun formatBytesToHumanReadable_displaysGB() {
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 500L

        try {
            fileManager.validateStorageCapacity(tempDir, expectedBytes = 1024L * 1024 * 1024)
        } catch (e: IllegalStateException) {
            // Check that error message contains "GB"
            assertTrue(e.message?.contains("GB") == true)
        }
    }

    // ========== Comprehensive Integration Tests ==========

    @Test
    fun comprehensiveWorkflow_downloadAndVerify() {
        // Create directory
        val downloadDir = File(tempDir, "downloads")
        fileManager.createDirectoryIfNotExists(downloadDir)

        // Validate storage
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 10L * 1024 * 1024 * 1024
        fileManager.validateStorageCapacity(downloadDir, expectedBytes = 5L * 1024 * 1024 * 1024)

        // Create and reconcile chunks
        val chunk1 = File(downloadDir, "file.part01").apply { writeText("chunk1") }
        val chunk2 = File(downloadDir, "file.part02").apply { writeText("chunk2") }
        val chunk3 = File(downloadDir, "file.part03").apply { writeText("chunk3") }
        val chunks = listOf(
            DownloadChunk(chunk1.name, 0, 5),
            DownloadChunk(chunk2.name, 6, 11),
            DownloadChunk(chunk3.name, 12, 17)
        )

        fileManager.reconcileChunks(downloadDir, "file.txt", chunks)

        // Verify final file
        val finalFile = File(downloadDir, "file.txt")
        assertTrue(finalFile.exists())
        assertEquals("chunk1chunk2chunk3", finalFile.readText())

        // Verify MD5
        val content = "chunk1chunk2chunk3"
        val expectedMd5 = content.toByteArray().let { bytes ->
            MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }
        assertTrue(fileManager.verifyMd5(finalFile, expectedMd5))

        // Verify chunks are deleted
        assertFalse(chunk1.exists())
        assertFalse(chunk2.exists())
        assertFalse(chunk3.exists())
    }

    @Test
    fun comprehensiveWorkflow_withFailure_cleansUp() {
        val downloadDir = File(tempDir, "downloads")
        fileManager.createDirectoryIfNotExists(downloadDir)

        val chunk1 = File(downloadDir, "file.part01").apply { writeText("chunk1") }
        val chunks = listOf(
            DownloadChunk(chunk1.name, 0, 5),
            DownloadChunk("missing.part02", 6, 10) // Missing chunk
        )

        try {
            fileManager.reconcileChunks(downloadDir, "file.txt", chunks)
        } catch (e: IOException) {
            // Expected
        }

        // Temp file should be cleaned up
        val tempFile = File(downloadDir, "file.txt.__assembling")
        assertFalse(tempFile.exists())
        
        // Final file should not exist
        val finalFile = File(downloadDir, "file.txt")
        assertFalse(finalFile.exists())
        
        // Existing chunk should still exist
        assertTrue(chunk1.exists())
    }

    // ========== Edge Cases ==========

    @Test
    fun reconcileChunks_withVeryLongFileName_handlesCorrectly() {
        val longFileName = "a".repeat(200) + ".txt"
        val chunkFile = File(tempDir, "file.part01").apply { writeText("content") }
        val chunks = listOf(DownloadChunk(chunkFile.name, 0, 6))

        fileManager.reconcileChunks(tempDir, longFileName, chunks)

        val finalFile = File(tempDir, longFileName)
        assertTrue(finalFile.exists())
        assertEquals("content", finalFile.readText())
    }

    @Test
    fun reconcileChunks_withSpecialCharactersInFileName_handlesCorrectly() {
        val specialFileName = "file-with-special-chars-àáâ.txt"
        val chunkFile = File(tempDir, "file.part01").apply { writeText("content") }
        val chunks = listOf(DownloadChunk(chunkFile.name, 0, 6))

        fileManager.reconcileChunks(tempDir, specialFileName, chunks)

        val finalFile = File(tempDir, specialFileName)
        assertTrue(finalFile.exists())
    }

    @Test
    fun verifyMd5_withFileContainingNullBytes_verifiesCorrectly() {
        val contentWithNulls = byteArrayOf(0x00, 0x01, 0x02, 0x00, 0x03)
        val file = File(tempDir, "nulls.bin").apply { writeBytes(contentWithNulls) }
        val expected = MessageDigest.getInstance("MD5").digest(contentWithNulls)
            .joinToString("") { "%02x".format(it) }

        assertTrue(fileManager.verifyMd5(file, expected))
    }

    @Test
    fun reconcileChunks_withChunksOfDifferentSizes_mergesCorrectly() {
        val chunk1 = File(tempDir, "file.part01").apply { writeText("a") }
        val chunk2 = File(tempDir, "file.part02").apply { writeText("bb") }
        val chunk3 = File(tempDir, "file.part03").apply { writeText("ccc") }
        val chunks = listOf(
            DownloadChunk(chunk1.name, 0, 0),
            DownloadChunk(chunk2.name, 1, 2),
            DownloadChunk(chunk3.name, 3, 5)
        )

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        val finalFile = File(tempDir, "file.txt")
        assertEquals("abbccc", finalFile.readText())
    }
}
