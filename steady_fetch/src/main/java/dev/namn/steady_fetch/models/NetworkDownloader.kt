package dev.namn.steady_fetch.models

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

/**
 * Handles all network-related operations for file downloads.
 * Manages OkHttp client instance and provides network utility methods.
 */
internal class NetworkDownloader {
    companion object {
        private const val TAG = "NetworkDownloader"
        private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 10L
        private const val DEFAULT_READ_TIMEOUT_SECONDS = 10L
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val downloadProgressStore = ConcurrentHashMap<Long, ConcurrentHashMap<String, DownloadChunkWithProgress>>()

    /**
     * Gets the total file size in bytes from the given URL using a HEAD request.
     *
     * @param url The URL to get the file size from
     * @param headers Optional custom headers to include in the request
     * @return The file size in bytes, or null if it cannot be determined
     */
    suspend fun getTotalBytesOfTheFile(url: String, headers: Map<String, String> = emptyMap()): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .head()

                headers.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }

                val request = requestBuilder.build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val contentLength = response.header("Content-Length")
                        contentLength?.toLongOrNull()
                    } else {
                        Log.w(TAG, "Failed to get file size: HTTP ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file size from URL: $url", e)
                null
            }
        }
    }

    suspend fun startDownload(downloadId: Long, request: DownloadRequest, parallelism: Int) {
        require(request.fileName.isNotBlank()) { "fileName must not be blank" }

        val chunkList = prepareChunksForDownload(request)

        val progressMap = ConcurrentHashMap<String, DownloadChunkWithProgress>()
        downloadProgressStore[downloadId] = progressMap

        chunkList.forEach { chunk ->
            progressMap[chunk.name] = DownloadChunkWithProgress(
                chunk = chunk,
                progress = 0f,
                downloadedBytes = 0L
            )
        }

        coroutineScope {
            val effectiveParallelism = parallelism.coerceIn(1, chunkList.size.coerceAtLeast(1))
            val semaphore = Semaphore(effectiveParallelism)

            val jobs = chunkList.map { chunk ->
                launch {
                    semaphore.withPermit {
                        val targetFile = File(request.outputDir, chunk.name)
                        try {
                            downloadChunkToFile(
                                url = request.url,
                                headers = request.headers,
                                chunk = chunk,
                                outputFile = targetFile,
                                downloadId = downloadId
                            )
                        } catch (cancelled: CancellationException) {
                            updateChunkProgress(downloadId, chunk, 0L, expectedBytesForChunk(chunk))
                            throw cancelled
                        } catch (error: Exception) {
                            Log.e(TAG, "Chunk ${chunk.chunkIndex} failed", error)
                            throw error
                        }
                    }
                }
            }

            jobs.forEach { it.join() }
        }
    }

    fun getDownloadProgressSnapshot(downloadId: Long): Map<String, DownloadChunkWithProgress> =
        downloadProgressStore[downloadId]?.toMap() ?: emptyMap()

    fun clearProgress(downloadId: Long) {
        downloadProgressStore.remove(downloadId)
    }

    private suspend fun downloadChunkToFile(
        url: String,
        headers: Map<String, String>,
        chunk: DownloadChunk,
        outputFile: File,
        downloadId: Long
    ) = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        if (chunk.startRange != null && chunk.endRange != null) {
            requestBuilder.addHeader("Range", "bytes=${chunk.startRange}-${chunk.endRange}")
        }

        val request = requestBuilder.build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to download chunk ${chunk.chunkIndex}: HTTP ${response.code}")
                }

                val body = response.body
                    ?: throw IOException("Empty response body while downloading chunk ${chunk.chunkIndex}")

                val expectedBytes = determineExpectedBytes(chunk, body.contentLength())
                writeBodyToFile(body, outputFile, chunk, expectedBytes, downloadId)
                Log.d(TAG, "Downloaded chunk ${chunk.chunkIndex} (${chunk.name}) to ${outputFile.absolutePath}")
            }
        } catch (ioe: IOException) {
            Log.e(TAG, "Network error downloading chunk ${chunk.chunkIndex}", ioe)
            throw ioe
        }
    }

    private fun writeBodyToFile(
        body: ResponseBody,
        file: File,
        chunk: DownloadChunk,
        expectedBytes: Long?,
        downloadId: Long
    ) {
        ensureDirectoryExists(file.parentFile)
        FileOutputStream(file, false).use { outputStream ->
            body.byteStream().use { inputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloadedBytes = 0L

                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    outputStream.write(buffer, 0, read)
                    downloadedBytes += read
                    updateChunkProgress(downloadId, chunk, downloadedBytes, expectedBytes)
                }

                outputStream.flush()
                updateChunkProgress(downloadId, chunk, expectedBytes ?: downloadedBytes, expectedBytes)
            }
        }
    }

    private fun updateChunkProgress(
        downloadId: Long,
        chunk: DownloadChunk,
        downloadedBytes: Long,
        expectedBytes: Long?
    ) {
        val progress = if (expectedBytes != null && expectedBytes > 0) {
            (downloadedBytes.coerceAtMost(expectedBytes).toDouble() / expectedBytes).toFloat()
        } else {
            -1f
        }

        downloadProgressStore[downloadId]?.compute(chunk.name) { _, _ ->
            DownloadChunkWithProgress(
                chunk = chunk,
                progress = progress,
                downloadedBytes = downloadedBytes
            )
        }
    }

    private fun determineExpectedBytes(chunk: DownloadChunk, responseContentLength: Long): Long? = when {
        responseContentLength > 0 -> responseContentLength
        else -> expectedBytesForChunk(chunk)
    }

    //todo: can chunk prep and chunk range array caclcutaion collapse into one funciton
    private fun prepareChunksForDownload(request: DownloadRequest): List<DownloadChunk> {
        val chunks = request.chunks?.takeIf { it.isNotEmpty() }
        if (chunks != null) return chunks

        val fallbackChunk = DownloadChunk(
            downloadId = -1L,
            name = request.fileName,
            chunkIndex = 0,
            totalBytes = null,
            startRange = null,
            endRange = null
        )
        request.chunks = listOf(fallbackChunk)
        return request.chunks ?: listOf(fallbackChunk)
    }

    //todo: isn't this already done in the controller: remove this
    private fun ensureDirectoryExists(dir: File?) {
        if (dir != null && !dir.exists()) {
            if (!dir.mkdirs() && !dir.exists()) {
                throw IOException("Unable to create directory: ${dir.absolutePath}")
            }
        }
    }
}
