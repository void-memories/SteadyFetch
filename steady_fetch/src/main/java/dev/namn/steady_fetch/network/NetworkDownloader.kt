package dev.namn.steady_fetch.network

import android.util.Log
import dev.namn.steady_fetch.Constants
import dev.namn.steady_fetch.datamodels.DownloadChunk
import dev.namn.steady_fetch.datamodels.DownloadChunkWithProgress
import dev.namn.steady_fetch.datamodels.DownloadRequest
import dev.namn.steady_fetch.managers.ChunkManager
import dev.namn.steady_fetch.progress.DownloadProgressStore
import dev.namn.steady_fetch.managers.FileManager
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit

internal class NetworkDownloader(
    private val okHttpClient: OkHttpClient = buildOkHttpClient(),
    private val progressStore: DownloadProgressStore,
    private val fileManager: FileManager
) {
    companion object {
        private fun buildOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(Constants.DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchFileContentLength(url: String, headers: Map<String, String> = emptyMap()): Long? {
        return withContext(Dispatchers.IO) {
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
                    Log.w(Constants.TAG_NETWORK_DOWNLOADER, "Failed to get file size: HTTP ${response.code}")
                    null
                }
            }
        }
    }

    suspend fun executeDownload(downloadId: Long, request: DownloadRequest, parallelism: Int) {
        require(request.fileName.isNotBlank()) { "fileName must not be blank" }

        val chunkList = prepareDownloadChunks(request)
        progressStore.initializeDownloadProgress(downloadId, chunkList)

        coroutineScope {
            val effectiveParallelism = parallelism.coerceIn(1, chunkList.size.coerceAtLeast(1))
            val semaphore = Semaphore(effectiveParallelism)

            val jobs = chunkList.map { chunk ->
                launch {
                    semaphore.withPermit {
                        val targetFile = File(request.outputDir, chunk.name)
                        downloadChunkAndWriteToFile(
                            url = request.url,
                            headers = request.headers,
                            chunk = chunk,
                            outputFile = targetFile,
                            downloadId = downloadId
                        )
                    }
                }
            }

            jobs.forEach { it.join() }
        }
    }

    fun getProgressSnapshot(downloadId: Long): Map<String, DownloadChunkWithProgress> =
        progressStore.getProgressSnapshot(downloadId)

    fun clearDownloadProgress(downloadId: Long) {
        progressStore.clearDownloadProgress(downloadId)
    }

    private suspend fun downloadChunkAndWriteToFile(
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

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download chunk ${chunk.chunkIndex}: HTTP ${response.code}")
            }

            val body = response.body
                ?: throw IOException("Empty response body while downloading chunk ${chunk.chunkIndex}")

            val expectedBytes = calculateExpectedChunkBytes(chunk, body.contentLength())
            fileManager.writeChunkToFile(body, outputFile, chunk, expectedBytes, downloadId)
            Log.d(Constants.TAG_NETWORK_DOWNLOADER, "Downloaded chunk ${chunk.chunkIndex} (${chunk.name}) to ${outputFile.absolutePath}")
        }
    }

    private fun calculateExpectedChunkBytes(chunk: DownloadChunk, responseContentLength: Long): Long? = when {
        responseContentLength > 0 -> responseContentLength
        else -> ChunkManager.calculateExpectedBytesForChunk(chunk)
    }

    private fun prepareDownloadChunks(request: DownloadRequest): List<DownloadChunk> {
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
}
