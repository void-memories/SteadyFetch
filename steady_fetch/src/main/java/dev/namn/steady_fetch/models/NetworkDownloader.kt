package dev.namn.steady_fetch.models

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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

    private val downloadProgressMap = ConcurrentHashMap<String, DownloadChunkWithProgress>()

    /**
     * Gets the total file size in bytes from the given URL using a HEAD request.
     *
     * @param url The URL to get the file size from
     * @param headers Optional custom headers to include in the request
     * @return The file size in bytes, or null if it cannot be determined
     */
    fun getTotalBytesOfTheFile(url: String, headers: Map<String, String> = emptyMap()): Long? {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .head() // Use HEAD request to avoid downloading the entire file

            // Add custom headers if provided
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

    fun startDownload(request: DownloadRequest) {
        require(request.fileName.isNotBlank()) { "fileName must not be blank" }

        val chunkList = prepareChunksForDownload(request)

        downloadProgressMap.clear()
        chunkList.forEach { chunk ->
            downloadProgressMap[chunk.name] = DownloadChunkWithProgress(
                chunk = chunk,
                progress = 0f,
                downloadedBytes = 0L
            )
        }

        chunkList.forEach { chunk ->
            val targetFile = File(request.outputDir, chunk.name)
            downloadChunkToFile(
                url = request.url,
                headers = request.headers,
                chunk = chunk,
                outputFile = targetFile
            )
        }
    }

    fun getDownloadProgressSnapshot(): Map<String, DownloadChunkWithProgress> = downloadProgressMap.toMap()

    private fun downloadChunkToFile(
        url: String,
        headers: Map<String, String>,
        chunk: DownloadChunk,
        outputFile: File
    ) {
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

                val expectedBytes = resolveExpectedBytes(chunk, body.contentLength())
                writeBodyToFile(body, outputFile, chunk, expectedBytes)
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
        expectedBytes: Long?
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
                    updateChunkProgress(chunk, downloadedBytes, expectedBytes)
                }

                outputStream.flush()
                updateChunkProgress(chunk, expectedBytes ?: downloadedBytes, expectedBytes)
            }
        }
    }

    private fun updateChunkProgress(chunk: DownloadChunk, downloadedBytes: Long, expectedBytes: Long?) {
        val progress = if (expectedBytes != null && expectedBytes > 0) {
            (downloadedBytes.coerceAtMost(expectedBytes).toDouble() / expectedBytes).toFloat()
        } else {
            -1f
        }

        downloadProgressMap.compute(chunk.name) { _, _ ->
            DownloadChunkWithProgress(
                chunk = chunk,
                progress = progress,
                downloadedBytes = downloadedBytes
            )
        }
    }

    private fun resolveExpectedBytes(chunk: DownloadChunk, responseContentLength: Long): Long? {
        return when {
            responseContentLength > 0 -> responseContentLength
            chunk.startRange != null && chunk.endRange != null -> chunk.endRange - chunk.startRange + 1
            chunk.totalBytes != null && chunk.totalBytes > 0 -> chunk.totalBytes
            else -> null
        }
    }

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

    private fun ensureDirectoryExists(dir: File?) {
        if (dir != null && !dir.exists()) {
            if (!dir.mkdirs() && !dir.exists()) {
                throw IOException("Unable to create directory: ${dir.absolutePath}")
            }
        }
    }
}
