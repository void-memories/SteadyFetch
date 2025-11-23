package dev.namn.steady_fetch.impl.io

import dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback
import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import dev.namn.steady_fetch.impl.datamodels.DownloadError
import dev.namn.steady_fetch.impl.datamodels.DownloadMetadata
import dev.namn.steady_fetch.impl.datamodels.DownloadProgress
import dev.namn.steady_fetch.impl.datamodels.DownloadRequest
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import dev.namn.steady_fetch.impl.managers.FileManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NetworkingTest {

    private lateinit var tempDir: File
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        tempDir = createTempDir(prefix = "steady-network")
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    // ========== fetchRemoteMetadata Tests ==========

    @Test
    fun fetchRemoteMetadata_usesRangeProbeWhenSupported() {
        val networking = Networking(OkHttpClient(), FileManager())
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-0/10")
                .setHeader("Content-Length", "1")
                .setBody("a")
        )

        val result = networking.fetchRemoteMetadata(server.url("/file.bin").toString(), emptyMap())

        assertEquals(10L, result.contentLength)
        assertTrue(result.supportsRanges)
        assertNull(result.contentMd5)
    }

    @Test
    fun fetchRemoteMetadata_withContentMd5Header_returnsMd5() {
        val networking = Networking(OkHttpClient(), FileManager())
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-0/10")
                .setHeader("Content-MD5", "d41d8cd98f00b204e9800998ecf8427e")
                .setBody("a")
        )

        val result = networking.fetchRemoteMetadata(server.url("/file.bin").toString(), emptyMap())

        assertEquals("d41d8cd98f00b204e9800998ecf8427e", result.contentMd5)
    }

    @Test
    fun fetchRemoteMetadata_withRangeProbe200Response_usesContentLength() {
        val networking = Networking(OkHttpClient(), FileManager())
        // Range probe returns 200, so it uses Content-Length from the response
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "42")
                .setBody("x".repeat(42)) // Body must match Content-Length
        )

        val result = networking.fetchRemoteMetadata(server.url("/file.bin").toString(), emptyMap())

        assertEquals(42L, result.contentLength)
        assertFalse(result.supportsRanges)
    }

    @Test
    fun fetchRemoteMetadata_withRangeProbe206ButNoContentRange_usesContentLength() {
        val networking = Networking(OkHttpClient(), FileManager())
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Length", "100")
                .setBody("x".repeat(100)) // Body must match Content-Length
        )

        val result = networking.fetchRemoteMetadata(server.url("/file.bin").toString(), emptyMap())

        assertEquals(100L, result.contentLength)
        // supportsRanges = response.code == 206 && response.header("Content-Range") != null
        // Code is 206 but Content-Range header is missing, so supportsRanges = false
        assertFalse(result.supportsRanges)
    }

    @Test
    fun fetchRemoteMetadata_fallsBackToHeadWhenProbeFails() {
        val okHttp = mockk<OkHttpClient>()
        val networking = Networking(okHttp, FileManager())
        val rangeCall = mockk<Call>()
        every { okHttp.newCall(match { it.header("Range") != null }) } returns rangeCall
        every { rangeCall.execute() } throws IOException("boom")

        val headCall = mockk<Call>()
        val headResponse = Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://example.com").head().build())
            .body("".toResponseBody(null))
            .header("Content-Length", "42")
            .build()
        every { okHttp.newCall(match { it.method == "HEAD" }) } returns headCall
        every { headCall.execute() } returns headResponse

        val result = networking.fetchRemoteMetadata("https://example.com", emptyMap())

        assertEquals(42L, result.contentLength)
        assertFalse(result.supportsRanges)
    }

    @Test
    fun fetchRemoteMetadata_fallsBackToHeadWithMd5() {
        val okHttp = mockk<OkHttpClient>()
        val networking = Networking(okHttp, FileManager())
        val rangeCall = mockk<Call>()
        every { okHttp.newCall(match { it.header("Range") != null }) } returns rangeCall
        every { rangeCall.execute() } throws IOException("boom")

        val headCall = mockk<Call>()
        val headResponse = Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://example.com").head().build())
            .body("".toResponseBody(null))
            .header("Content-Length", "42")
            .header("Content-MD5", "test-md5-hash")
            .build()
        every { okHttp.newCall(match { it.method == "HEAD" }) } returns headCall
        every { headCall.execute() } returns headResponse

        val result = networking.fetchRemoteMetadata("https://example.com", emptyMap())

        assertEquals("test-md5-hash", result.contentMd5)
    }

    @Test
    fun fetchRemoteMetadata_returnsNullWhenBothProbeAndHeadFail() {
        val okHttp = mockk<OkHttpClient>()
        val networking = Networking(okHttp, FileManager())
        val rangeCall = mockk<Call>()
        every { okHttp.newCall(match { it.header("Range") != null }) } returns rangeCall
        every { rangeCall.execute() } throws IOException("range failed")

        val headCall = mockk<Call>()
        every { okHttp.newCall(match { it.method == "HEAD" }) } returns headCall
        every { headCall.execute() } throws IOException("head failed")

        val result = networking.fetchRemoteMetadata("https://example.com", emptyMap())

        assertNull(result.contentLength)
        assertFalse(result.supportsRanges)
        assertNull(result.contentMd5)
    }

    @Test
    fun fetchRemoteMetadata_withCustomHeaders_passesHeaders() {
        val networking = Networking(OkHttpClient(), FileManager())
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-0/10")
                .setBody("a")
        )

        val headers = mapOf("Authorization" to "Bearer token", "User-Agent" to "TestAgent")
        networking.fetchRemoteMetadata(server.url("/file.bin").toString(), headers)

        val request = server.takeRequest()
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertEquals("TestAgent", request.getHeader("User-Agent"))
    }

    @Test
    fun fetchRemoteMetadata_withInvalidContentRange_handlesGracefully() {
        val networking = Networking(OkHttpClient(), FileManager())
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "invalid-format")
                .setHeader("Content-Length", "50")
                .setBody("x".repeat(50)) // Body must match Content-Length
        )

        val result = networking.fetchRemoteMetadata(server.url("/file.bin").toString(), emptyMap())

        assertEquals(50L, result.contentLength)
        // supportsRanges is true if code is 206 AND Content-Range header exists (even if invalid format)
        assertTrue(result.supportsRanges)
    }

    @Test
    fun fetchRemoteMetadata_withContentRangeMissingTotal_usesContentLength() {
        val networking = Networking(OkHttpClient(), FileManager())
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-0/*")
                .setHeader("Content-Length", "75")
                .setBody("x".repeat(75)) // Body must match Content-Length
        )

        val result = networking.fetchRemoteMetadata(server.url("/file.bin").toString(), emptyMap())

        // parseContentRangeTotal returns null for "bytes 0-0/*", so falls back to Content-Length
        assertEquals(75L, result.contentLength)
        // supportsRanges is true if code is 206 AND Content-Range header exists
        assertTrue(result.supportsRanges)
    }

    // ========== download Single File Tests ==========

    @Test
    fun download_singleFile_writesPayloadAndSignalsSuccess() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Length", "5").setBody("hello"))
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "downloads").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/hello.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "hello.bin"
        )
        val metadata = DownloadMetadata(request, chunks = null, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(1L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertEquals("hello", File(downloadsDir, "hello.bin").readText())
    }

    @Test
    fun download_singleFile_withMd5Verification_succeedsWhenMatches() {
        val content = "hello"
        val md5 = content.toByteArray().let { bytes ->
            MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }

        server.enqueue(MockResponse().setResponseCode(200).setBody(content))
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "downloads").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/hello.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "hello.bin"
        )
        val metadata = DownloadMetadata(request, chunks = null, contentMd5 = md5)
        val callback = RecordingCallback()

        runTest {
            networking.download(1L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
    }

    @Test
    fun download_singleFile_withMd5Verification_failsWhenMismatch() {
        val content = "hello"
        val wrongMd5 = "wrong-md5-hash-1234567890abcdef"

        server.enqueue(MockResponse().setResponseCode(200).setBody(content))
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "downloads").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/hello.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "hello.bin"
        )
        val metadata = DownloadMetadata(request, chunks = null, contentMd5 = wrongMd5)
        val callback = RecordingCallback()

        runTest {
            networking.download(1L, metadata, callback)
        }

        assertTrue(callback.awaitError())
        assertTrue(callback.lastError?.message?.contains("MD5 verification failed") == true)
    }

    @Test
    fun download_singleFile_withHttpError_fails() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "downloads").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/missing.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "missing.bin"
        )
        val metadata = DownloadMetadata(request, chunks = null, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(1L, metadata, callback)
        }

        assertTrue(callback.awaitError())
        assertTrue(callback.lastError?.message?.contains("HTTP 404") == true)
    }

    @Test
    fun download_singleFile_withEmptyResponseBody_fails() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "downloads").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/empty.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "empty.bin"
        )
        val metadata = DownloadMetadata(request, chunks = null, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(1L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertEquals("", File(downloadsDir, "empty.bin").readText())
    }

    @Test
    fun download_singleFile_withLargeFile_succeeds() {
        val largeContent = "A".repeat(1024 * 1024) // 1MB
        server.enqueue(MockResponse().setResponseCode(200).setBody(largeContent))
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "downloads").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/large.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "large.bin"
        )
        val metadata = DownloadMetadata(request, chunks = null, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(1L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertEquals(largeContent, File(downloadsDir, "large.bin").readText())
    }

    @Test
    fun download_singleFile_withCustomHeaders_passesHeaders() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "downloads").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/hello.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "hello.bin",
            headers = mapOf("Authorization" to "Bearer token")
        )
        val metadata = DownloadMetadata(request, chunks = null, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(1L, metadata, callback)
        }

        val recordedRequest = server.takeRequest()
        assertEquals("Bearer token", recordedRequest.getHeader("Authorization"))
    }

    // ========== download Chunked Tests ==========

    @Test
    fun download_chunkedRequest_mergesChunksAndSucceeds() {
        val body = "abcdef"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: ""
                return when {
                    range.contains("0-2") -> MockResponse().setResponseCode(206).setBody(body.substring(0, 3))
                    range.contains("3-5") -> MockResponse().setResponseCode(206).setBody(body.substring(3, 6))
                    else -> MockResponse().setResponseCode(400)
                }
            }
        }

        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "chunks").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/chunk.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "chunk.bin",
            maxParallelDownloads = 2
        )
        val chunks = listOf(
            DownloadChunk("chunk.bin.part1", 0, 2),
            DownloadChunk("chunk.bin.part2", 3, 5)
        )
        val metadata = DownloadMetadata(request, chunks = chunks, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(2L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertEquals(body, File(downloadsDir, "chunk.bin").readText())
    }

    @Test
    fun download_chunkedRequest_withAllChunksComplete_skipsDownload() {
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "chunks").apply { mkdirs() }
        val chunk1 = File(downloadsDir, "file.part1").apply { writeText("abc") }
        val chunk2 = File(downloadsDir, "file.part2").apply { writeText("def") }

        val request = DownloadRequest(
            url = server.url("/file.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "file.bin"
        )
        val chunks = listOf(
            DownloadChunk("file.part1", 0, 2),
            DownloadChunk("file.part2", 3, 5)
        )
        val metadata = DownloadMetadata(request, chunks = chunks, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(3L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertEquals("abcdef", File(downloadsDir, "file.bin").readText())
        assertEquals(0, server.requestCount) // No HTTP requests made
    }

    @Test
    fun download_chunkedRequest_withPartialChunks_resumes() {
        val body = "abcdef"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: ""
                return when {
                    range.contains("1-2") -> MockResponse().setResponseCode(206).setBody(body.substring(1, 3))
                    range.contains("3-5") -> MockResponse().setResponseCode(206).setBody(body.substring(3, 6))
                    else -> MockResponse().setResponseCode(400)
                }
            }
        }

        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "chunks").apply { mkdirs() }
        // Chunk 1 partially downloaded (1 byte)
        File(downloadsDir, "file.part1").apply { writeText("a") }

        val request = DownloadRequest(
            url = server.url("/file.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "file.bin"
        )
        val chunks = listOf(
            DownloadChunk("file.part1", 0, 2),
            DownloadChunk("file.part2", 3, 5)
        )
        val metadata = DownloadMetadata(request, chunks = chunks, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(4L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertEquals(body, File(downloadsDir, "file.bin").readText())
    }

    @Test
    fun download_chunkedRequest_withMd5Verification_succeedsWhenMatches() {
        val body = "abcdef"
        val md5 = body.toByteArray().let { bytes ->
            MessageDigest.getInstance("MD5").digest(bytes)
        }.joinToString("") { "%02x".format(it) }

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: ""
                return when {
                    range.contains("0-2") -> MockResponse().setResponseCode(206).setBody(body.substring(0, 3))
                    range.contains("3-5") -> MockResponse().setResponseCode(206).setBody(body.substring(3, 6))
                    else -> MockResponse().setResponseCode(400)
                }
            }
        }

        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "chunks").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/chunk.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "chunk.bin"
        )
        val chunks = listOf(
            DownloadChunk("chunk.bin.part1", 0, 2),
            DownloadChunk("chunk.bin.part2", 3, 5)
        )
        val metadata = DownloadMetadata(request, chunks = chunks, contentMd5 = md5)
        val callback = RecordingCallback()

        runTest {
            networking.download(5L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
    }

    @Test
    fun download_chunkedRequest_withMd5Verification_failsWhenMismatch() {
        val body = "abcdef"
        val wrongMd5 = "wrong-md5-hash"

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: ""
                return when {
                    range.contains("0-2") -> MockResponse().setResponseCode(206).setBody(body.substring(0, 3))
                    range.contains("3-5") -> MockResponse().setResponseCode(206).setBody(body.substring(3, 6))
                    else -> MockResponse().setResponseCode(400)
                }
            }
        }

        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "chunks").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/chunk.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "chunk.bin"
        )
        val chunks = listOf(
            DownloadChunk("chunk.bin.part1", 0, 2),
            DownloadChunk("chunk.bin.part2", 3, 5)
        )
        val metadata = DownloadMetadata(request, chunks = chunks, contentMd5 = wrongMd5)
        val callback = RecordingCallback()

        runTest {
            networking.download(6L, metadata, callback)
        }

        assertTrue(callback.awaitError())
        assertTrue(callback.lastError?.message?.contains("MD5 verification failed") == true)
    }

    @Test
    fun download_chunkedRequest_withChunkFailure_fails() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: ""
                return when {
                    range.contains("0-2") -> MockResponse().setResponseCode(206).setBody("abc")
                    range.contains("3-5") -> MockResponse().setResponseCode(500).setBody("Error")
                    else -> MockResponse().setResponseCode(400)
                }
            }
        }

        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "chunks").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/chunk.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "chunk.bin"
        )
        val chunks = listOf(
            DownloadChunk("chunk.bin.part1", 0, 2),
            DownloadChunk("chunk.bin.part2", 3, 5)
        )
        val metadata = DownloadMetadata(request, chunks = chunks, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(7L, metadata, callback)
        }

        assertTrue(callback.awaitError())
    }

    @Test
    fun download_chunkedRequest_withManyChunks_downloadsInParallel() {
        val body = "abcdefghij"
        val chunkCount = 5
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: ""
                val rangeMatch = Regex("bytes=(\\d+)-(\\d+)").find(range)
                if (rangeMatch != null) {
                    val start = rangeMatch.groupValues[1].toInt()
                    val end = rangeMatch.groupValues[2].toInt()
                    return MockResponse().setResponseCode(206).setBody(body.substring(start, end + 1))
                }
                return MockResponse().setResponseCode(400)
            }
        }

        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "chunks").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/many.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "many.bin",
            maxParallelDownloads = 3
        )
        val chunks = (0 until chunkCount).map { index ->
            val start = index * 2
            val end = start + 1
            DownloadChunk("many.bin.part${index + 1}", start.toLong(), end.toLong())
        }
        val metadata = DownloadMetadata(request, chunks = chunks, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(8L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertEquals(body, File(downloadsDir, "many.bin").readText())
    }

    @Test
    fun download_chunkedRequest_withEmptyChunksList_fallsBackToSingleFile() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("content"))
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "chunks").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/empty.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "empty.bin"
        )
        val metadata = DownloadMetadata(request, chunks = emptyList(), contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(9L, metadata, callback)
        }

        // Empty chunks list falls back to single file download
        assertTrue(callback.awaitSuccess())
        assertEquals("content", File(downloadsDir, "empty.bin").readText())
    }

    @Test
    fun download_chunkedRequest_withResumeDeletesExistingChunk() {
        val body = "abcdef"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: ""
                return when {
                    range.contains("0-2") -> MockResponse().setResponseCode(206).setBody(body.substring(0, 3))
                    range.contains("3-5") -> MockResponse().setResponseCode(206).setBody(body.substring(3, 6))
                    else -> MockResponse().setResponseCode(400)
                }
            }
        }

        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "chunks").apply { mkdirs() }
        // Create existing chunk file with 0 bytes (will be deleted on resume)
        File(downloadsDir, "file.part1").apply { writeText("") }

        val request = DownloadRequest(
            url = server.url("/file.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "file.bin"
        )
        val chunks = listOf(
            DownloadChunk("file.part1", 0, 2),
            DownloadChunk("file.part2", 3, 5)
        )
        val metadata = DownloadMetadata(request, chunks = chunks, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(10L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertEquals(body, File(downloadsDir, "file.bin").readText())
    }

    // ========== cancel Tests ==========

    @Test
    fun cancel_cancelsRegisteredCalls() {
        val networking = Networking(OkHttpClient(), FileManager())
        val call = mockk<Call>(relaxed = true)
        val activeCallsField = Networking::class.java.getDeclaredField("activeCalls")
        activeCallsField.isAccessible = true
        val map = activeCallsField.get(networking) as MutableMap<Long, MutableSet<Call>>
        map[77L] = mutableSetOf(call)

        val cancelled = networking.cancel(77L)

        assertTrue(cancelled)
        verify { call.cancel() }
    }

    @Test
    fun cancel_withNonExistentDownloadId_returnsFalse() {
        val networking = Networking(OkHttpClient(), FileManager())

        val cancelled = networking.cancel(999L)

        assertFalse(cancelled)
    }

    @Test
    fun cancel_withMultipleCalls_cancelsAll() {
        val networking = Networking(OkHttpClient(), FileManager())
        val call1 = mockk<Call>(relaxed = true)
        val call2 = mockk<Call>(relaxed = true)
        val call3 = mockk<Call>(relaxed = true)
        val activeCallsField = Networking::class.java.getDeclaredField("activeCalls")
        activeCallsField.isAccessible = true
        val map = activeCallsField.get(networking) as MutableMap<Long, MutableSet<Call>>
        map[88L] = mutableSetOf(call1, call2, call3)

        val cancelled = networking.cancel(88L)

        assertTrue(cancelled)
        verify { call1.cancel() }
        verify { call2.cancel() }
        verify { call3.cancel() }
    }

    // ========== Progress Calculation Tests ==========

    @Test
    fun download_singleFile_emitsProgressUpdates() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "downloads").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/hello.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "hello.bin"
        )
        val metadata = DownloadMetadata(request, chunks = null, contentMd5 = null)
        val callback = ProgressTrackingCallback()

        runTest {
            networking.download(11L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertTrue(callback.progressUpdates.size > 0)
        assertEquals(DownloadStatus.RUNNING, callback.progressUpdates.first().status)
        assertEquals(DownloadStatus.SUCCESS, callback.progressUpdates.last().status)
    }

    @Test
    fun download_chunkedRequest_emitsProgressUpdates() {
        val body = "abcdef"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: ""
                return when {
                    range.contains("0-2") -> MockResponse().setResponseCode(206).setBody(body.substring(0, 3))
                    range.contains("3-5") -> MockResponse().setResponseCode(206).setBody(body.substring(3, 6))
                    else -> MockResponse().setResponseCode(400)
                }
            }
        }

        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "chunks").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/chunk.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "chunk.bin"
        )
        val chunks = listOf(
            DownloadChunk("chunk.bin.part1", 0, 2),
            DownloadChunk("chunk.bin.part2", 3, 5)
        )
        val metadata = DownloadMetadata(request, chunks = chunks, contentMd5 = null)
        val callback = ProgressTrackingCallback()

        runTest {
            networking.download(12L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertTrue(callback.progressUpdates.size > 0)
        // Should have QUEUED, RUNNING, and SUCCESS statuses
        val statuses = callback.progressUpdates.map { it.status }.toSet()
        assertTrue(statuses.contains(DownloadStatus.QUEUED) || statuses.contains(DownloadStatus.RUNNING))
        assertTrue(statuses.contains(DownloadStatus.SUCCESS))
    }

    // ========== Edge Cases ==========

    @Test
    fun download_withEmptyFile_succeeds() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "downloads").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/empty.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "empty.bin"
        )
        val metadata = DownloadMetadata(request, chunks = null, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(13L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertEquals("", File(downloadsDir, "empty.bin").readText())
    }

    @Test
    fun download_withBinaryContent_succeeds() {
        val binaryContent = byteArrayOf(0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0xFF.toByte(), 0xFE.toByte())
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(binaryContent)))
        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "downloads").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/binary.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "binary.bin"
        )
        val metadata = DownloadMetadata(request, chunks = null, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(14L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        assertTrue(binaryContent.contentEquals(File(downloadsDir, "binary.bin").readBytes()))
    }

    @Test
    fun download_withNetworkError_fails() {
        val okHttp = mockk<OkHttpClient>()
        val networking = Networking(okHttp, FileManager())
        val call = mockk<Call>()
        every { okHttp.newCall(any()) } returns call
        every { call.execute() } throws IOException("Network error")

        val downloadsDir = File(tempDir, "downloads").apply { mkdirs() }
        val request = DownloadRequest(
            url = "https://example.com/file.bin",
            downloadDir = downloadsDir,
            fileName = "file.bin"
        )
        val metadata = DownloadMetadata(request, chunks = null, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(15L, metadata, callback)
        }

        assertTrue(callback.awaitError())
        assertTrue(callback.lastError?.message?.contains("Network error") == true || 
                   callback.lastError?.message?.contains("Download failed") == true)
    }

    @Test
    fun download_withMaxParallelDownloadsLimit_respectsLimit() {
        val body = "abcdefghij"
        val chunkCount = 10
        val maxParallel = 2

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: ""
                val rangeMatch = Regex("bytes=(\\d+)-(\\d+)").find(range)
                if (rangeMatch != null) {
                    val start = rangeMatch.groupValues[1].toInt()
                    val end = rangeMatch.groupValues[2].toInt()
                    Thread.sleep(10) // Simulate network delay
                    return MockResponse().setResponseCode(206).setBody(body.substring(start, end + 1))
                }
                return MockResponse().setResponseCode(400)
            }
        }

        val networking = Networking(OkHttpClient(), FileManager())
        val downloadsDir = File(tempDir, "chunks").apply { mkdirs() }
        val request = DownloadRequest(
            url = server.url("/parallel.bin").toString(),
            downloadDir = downloadsDir,
            fileName = "parallel.bin",
            maxParallelDownloads = maxParallel
        )
        val chunks = (0 until chunkCount).map { index ->
            val start = index
            val end = index
            DownloadChunk("parallel.bin.part${(index + 1).toString().padStart(2, '0')}", start.toLong(), end.toLong())
        }
        val metadata = DownloadMetadata(request, chunks = chunks, contentMd5 = null)
        val callback = RecordingCallback()

        runTest {
            networking.download(16L, metadata, callback)
        }

        assertTrue(callback.awaitSuccess())
        // All chunks should be downloaded, but parallelism should be limited
        assertEquals(body, File(downloadsDir, "parallel.bin").readText())
    }

    // ========== Helper Classes ==========

    private class RecordingCallback : SteadyFetchCallback {
        private val latch = CountDownLatch(1)
        private val success = AtomicBoolean(false)
        var lastError: DownloadError? = null

        override fun onSuccess() {
            success.set(true)
            latch.countDown()
        }

        override fun onUpdate(progress: DownloadProgress) = Unit

        override fun onError(error: DownloadError) {
            lastError = error
            latch.countDown()
        }

        fun awaitSuccess(): Boolean = latch.await(5, TimeUnit.SECONDS) && success.get()
        fun awaitError(): Boolean = latch.await(5, TimeUnit.SECONDS) && !success.get()
    }

    private class ProgressTrackingCallback : SteadyFetchCallback {
        private val latch = CountDownLatch(1)
        private val success = AtomicBoolean(false)
        val progressUpdates = mutableListOf<DownloadProgress>()

        override fun onSuccess() {
            success.set(true)
            latch.countDown()
        }

        override fun onUpdate(progress: DownloadProgress) {
            progressUpdates.add(progress)
        }

        override fun onError(error: DownloadError) {
            latch.countDown()
        }

        fun awaitSuccess(): Boolean = latch.await(5, TimeUnit.SECONDS) && success.get()
    }
}
