package dev.namn.steady_fetch.impl.io

import dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback
import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(null, result.contentMd5)
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

        networking.download(1L, metadata, callback)

        assertTrue(callback.awaitSuccess())
        assertEquals("hello", File(downloadsDir, "hello.bin").readText())
    }

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

        networking.download(2L, metadata, callback)

        assertTrue(callback.awaitSuccess())
        assertEquals(body, File(downloadsDir, "chunk.bin").readText())
    }

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

    private class RecordingCallback : SteadyFetchCallback {
        private val latch = CountDownLatch(1)
        private val success = AtomicBoolean(false)

        override fun onSuccess() {
            success.set(true)
            latch.countDown()
        }

        override fun onUpdate(progress: DownloadProgress) = Unit

        override fun onError(error: dev.namn.steady_fetch.impl.datamodels.DownloadError) {
            latch.countDown()
        }

        fun awaitSuccess(): Boolean = latch.await(5, TimeUnit.SECONDS) && success.get()
    }
}

