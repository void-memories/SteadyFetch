package dev.namn.steady_fetch.impl.managers

import android.os.StatFs
import android.util.Log
import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

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

    @Test
    fun createDirectoryIfNotExists_createsMissingDir() {
        val target = File(tempDir, "nested/dir")
        assertTrue(!target.exists())

        fileManager.createDirectoryIfNotExists(target)

        assertTrue(target.exists())
        assertTrue(target.isDirectory)
    }

    @Test
    fun reconcileChunks_mergesInOrderAndDeletesPieces() {
        val chunkOne = File(tempDir, "file.part01").apply { writeText("foo") }
        val chunkTwo = File(tempDir, "file.part02").apply { writeText("bar") }
        val chunks = listOf(
            DownloadChunk(chunkOne.name, 0, 2),
            DownloadChunk(chunkTwo.name, 3, 5)
        )

        fileManager.reconcileChunks(tempDir, "file.txt", chunks)

        val finalFile = File(tempDir, "file.txt")
        assertEquals("foobar", finalFile.readText())
        assertTrue(!chunkOne.exists() && !chunkTwo.exists())
    }

    @Test
    fun verifyMd5_returnsTrueWhenChecksumMatches() {
        val file = File(tempDir, "payload.bin").apply { writeText("hello-world") }
        val expected = "hello-world".toByteArray().let { bytes ->
            java.security.MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }

        assertTrue(fileManager.verifyMd5(file, expected))
    }

    @Test(expected = IllegalStateException::class)
    fun validateStorageCapacity_throwsWhenInsufficient() {
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns 10

        val required = File(tempDir, "any")
        fileManager.validateStorageCapacity(required, expectedBytes = 1000)
    }
}

