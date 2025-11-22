package dev.namn.steady_fetch.io

import android.util.Log
import dev.namn.steady_fetch.ChunkProgressTracker
import dev.namn.steady_fetch.Constants
import dev.namn.steady_fetch.SteadyFetchCallback
import dev.namn.steady_fetch.datamodels.DownloadChunk
import dev.namn.steady_fetch.datamodels.DownloadChunkProgress
import dev.namn.steady_fetch.datamodels.DownloadError
import dev.namn.steady_fetch.datamodels.DownloadMetadata
import dev.namn.steady_fetch.datamodels.DownloadProgress
import dev.namn.steady_fetch.datamodels.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class Networking(
    private val okHttpClient: OkHttpClient,
) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    fun getExpectedFileSize(url: String, headers: Map<String, String>): Long? {
        return try {
            val request = buildHeadRequest(url, headers)
            okHttpClient.newCall(request).execute().use { response ->
                val size = response.header("Content-Length")?.toLongOrNull()
                Log.d(Constants.TAG, "HEAD content-length for $url -> $size")
                size
            }
        } catch (e: IOException) {
            Log.w(Constants.TAG, "HEAD content-length failed for $url", e)
            null
        }
    }

    fun doesServerSupportChunking(url: String, headers: Map<String, String>): Boolean {
        return try {
            val request = buildHeadRequest(url, headers)
            okHttpClient.newCall(request).execute().use { response ->
                val supported = response.header("Accept-Ranges")?.lowercase() == "bytes"
                Log.d(Constants.TAG, "Server chunk support for $url -> $supported")
                supported
            }
        } catch (e: IOException) {
            Log.w(Constants.TAG, "HEAD chunk support failed for $url", e)
            false
        }
    }

    fun download(
        downloadMetadata: DownloadMetadata,
        callback: SteadyFetchCallback
    ) {
        val request = downloadMetadata.request
        val semaphore = Semaphore(request.maxParallelDownloads)
        val destination = File(request.downloadDir, request.fileName)

        scope.launch {
            Log.i(Constants.TAG, "Download started url=${request.url}")
            val chunks = downloadMetadata.chunks

            if (chunks.isNullOrEmpty()) {
                Log.d(Constants.TAG, "Starting single file download for ${request.url}")
                downloadSingleFile(
                    semaphore = semaphore,
                    url = request.url,
                    headers = request.headers,
                    destination = destination,
                    callback = callback
                )
            } else {
                Log.d(Constants.TAG, "Starting chunked download chunks=${chunks.size} url=${request.url}")
                downloadInChunks(
                    semaphore = semaphore,
                    chunks = chunks,
                    url = request.url,
                    headers = request.headers,
                    downloadDir = request.downloadDir,
                    callback = callback
                )
            }
        }
    }

    private fun buildHeadRequest(
        url: String,
        headers: Map<String, String>
    ): Request = buildRequest(url, headers) {
        head()
    }

    private fun buildRequest(
        url: String,
        headers: Map<String, String>,
        block: Request.Builder.() -> Unit = {}
    ): Request {
        return Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
                block()
            }
            .build()
    }

    private fun downloadFile(
        url: String,
        headers: Map<String, String>,
        destination: File
    ) {
        val request = buildRequest(url, headers)
        downloadToFile(request, destination)
    }

    private fun downloadChunkToFile(
        url: String,
        headers: Map<String, String>,
        downloadDir: File,
        chunk: DownloadChunk,
        onProgress: (Float) -> Unit
    ) {
        val request = buildRequest(url, headers) {
            addHeader("Range", "bytes=${chunk.start}-${chunk.end}")
        }

        val expectedBytes = (chunk.end - chunk.start + 1).coerceAtLeast(1L)
        val chunkFile = File(downloadDir, chunk.name)

        downloadToFile(
            request = request,
            destination = chunkFile
        ) { bytesCopied ->
            val progress = (bytesCopied.toDouble() / expectedBytes)
                .toFloat()
                .coerceIn(0f, 1f)
            onProgress(progress)
        }
    }

    private fun downloadToFile(
        request: Request,
        destination: File,
        progressListener: ((Long) -> Unit)? = null
    ) {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(Constants.TAG, "HTTP ${response.code} for ${request.url}")
                throw IOException("Request failed with HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty response body")

            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(Constants.DEFAULT_BUFFER_SIZE)
                    var bytesCopied = 0L

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        progressListener?.invoke(bytesCopied)
                    }
                }
            }
        }
    }

    private suspend fun downloadSingleFile(
        semaphore: Semaphore,
        url: String,
        headers: Map<String, String>,
        destination: File,
        callback: SteadyFetchCallback
    ) {
        semaphore.withPermit {
            Log.d(Constants.TAG, "Single file download running url=$url")
            callback.emitProgress(
                status = DownloadStatus.RUNNING,
                chunkProgress = emptyList(),
                overrideProgress = 0f
            )

            try {
                downloadFile(url, headers, destination)

                callback.emitProgress(
                    status = DownloadStatus.SUCCESS,
                    chunkProgress = emptyList(),
                    overrideProgress = 1f
                )
                Log.i(Constants.TAG, "Single file download completed url=$url")
                callback.onSuccess()
            } catch (e: Exception) {
                Log.e(Constants.TAG, "Single file download failed url=$url", e)
                callback.emitProgress(
                    status = DownloadStatus.FAILED,
                    chunkProgress = emptyList(),
                    overrideProgress = 0f
                )
                callback.onError(
                    DownloadError(
                        code = -1,
                        message = e.message ?: "Download failed"
                    )
                )
            }
        }
    }

    private suspend fun downloadInChunks(
        semaphore: Semaphore,
        chunks: List<DownloadChunk>,
        url: String,
        headers: Map<String, String>,
        downloadDir: File,
        callback: SteadyFetchCallback
    ) = coroutineScope {
        val completionSignal = AtomicBoolean(false)
        val completedChunks = AtomicInteger(0)
        val tracker = ChunkProgressTracker(chunks)

        Log.d(Constants.TAG, "Chunked download queued ${chunks.size} chunks")
        callback.emitProgress(
            status = DownloadStatus.QUEUED,
            chunkProgress = tracker.snapshot()
        )

        chunks.forEachIndexed { index, chunk ->
            launch {
                semaphore.withPermit {
                    if (completionSignal.get()) return@withPermit

                    Log.d(Constants.TAG, "Downloading chunk ${chunk.name}")
                    callback.emitProgress(
                        status = DownloadStatus.RUNNING,
                        chunkProgress = tracker.markRunning(index)
                    )

                    try {
                        downloadChunkToFile(
                            url = url,
                            headers = headers,
                            downloadDir = downloadDir,
                            chunk = chunk
                        ) { progress ->
                            callback.emitProgress(
                                status = DownloadStatus.RUNNING,
                                chunkProgress = tracker.updateProgress(index, progress)
                            )
                        }

                        val snapshot = tracker.markSuccess(index)
                        val finished = completedChunks.incrementAndGet()
                        val allFinished = finished == chunks.size

                        val status = if (allFinished) {
                            DownloadStatus.SUCCESS
                        } else {
                            DownloadStatus.RUNNING
                        }

                        callback.emitProgress(status, snapshot)

                        if (allFinished && completionSignal.compareAndSet(false, true)) {
                            Log.i(Constants.TAG, "All chunks completed")
                            callback.onSuccess()
                        }
                    } catch (e: Exception) {
                        Log.e(Constants.TAG, "Chunk ${chunk.name} failed", e)
                        val snapshot = tracker.markFailed(index)

                        callback.emitProgress(
                            status = DownloadStatus.FAILED,
                            chunkProgress = snapshot
                        )

                        if (completionSignal.compareAndSet(false, true)) {
                            callback.onError(
                                DownloadError(
                                    code = -1,
                                    message = e.message ?: "Download failed"
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun SteadyFetchCallback.emitProgress(
        status: DownloadStatus,
        chunkProgress: List<DownloadChunkProgress>,
        overrideProgress: Float? = null
    ) {
        val progressValue = overrideProgress ?: calculateOverallProgress(chunkProgress)
        onUpdate(
            DownloadProgress(
                status = status,
                progress = progressValue,
                chunkProgress = chunkProgress
            )
        )
    }

    private fun calculateOverallProgress(
        chunkProgress: List<DownloadChunkProgress>
    ): Float {
        if (chunkProgress.isEmpty()) return 0f
        val total = chunkProgress.sumOf { it.progress.toDouble() }
        return (total / chunkProgress.size)
            .toFloat()
            .coerceIn(0f, 1f)
    }
}
