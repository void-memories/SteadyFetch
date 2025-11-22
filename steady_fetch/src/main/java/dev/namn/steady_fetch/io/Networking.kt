package dev.namn.steady_fetch.io

import android.util.Log
import dev.namn.steady_fetch.ChunkProgressTracker
import dev.namn.steady_fetch.SteadyFetchCallback
import dev.namn.steady_fetch.Constants
import dev.namn.steady_fetch.datamodels.DownloadChunk
import dev.namn.steady_fetch.datamodels.DownloadChunkProgress
import dev.namn.steady_fetch.datamodels.DownloadError
import dev.namn.steady_fetch.datamodels.DownloadMetadata
import dev.namn.steady_fetch.datamodels.DownloadProgress
import dev.namn.steady_fetch.datamodels.DownloadStatus
import dev.namn.steady_fetch.managers.FileManager
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

internal class Networking(
    private val okHttpClient: OkHttpClient,
    private val fileManager: FileManager,
) {
    data class RemoteMetadata(
        val contentLength: Long?,
        val supportsRanges: Boolean,
        val contentMd5: String?
    )

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    fun fetchRemoteMetadata(url: String, headers: Map<String, String>): RemoteMetadata {
        val probeResult = tryRangeProbe(url, headers)
        if (probeResult != null) {
            return probeResult
        }

        val headResult = tryHeadForSize(url, headers)
        if (headResult != null) {
            return headResult
        }

        Log.w(Constants.TAG, "Unable to determine remote metadata for $url, proceeding without chunking")
        return RemoteMetadata(
            contentLength = null,
            supportsRanges = false,
            contentMd5 = null
        )
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
                    expectedMd5 = downloadMetadata.contentMd5,
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
                    fileName = request.fileName,
                    expectedMd5 = downloadMetadata.contentMd5,
                    callback = callback
                )
            }
        }
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
        alreadyDownloaded: Long,
        onProgress: (Float) -> Unit
    ) {
        val expectedBytes = chunkSize(chunk)
        val resumeBytes = alreadyDownloaded.coerceIn(0L, expectedBytes - 1)
        val rangeStart = chunk.start + resumeBytes
        val request = buildRequest(url, headers) {
            addHeader("Range", "bytes=$rangeStart-${chunk.end}")
        }

        val chunkFile = File(downloadDir, chunk.name)
        if (resumeBytes == 0L && chunkFile.exists()) {
            chunkFile.delete()
        }

        downloadToFile(
            request = request,
            destination = chunkFile,
            append = resumeBytes > 0
        ) { bytesCopied ->
            val total = (resumeBytes + bytesCopied).coerceAtMost(expectedBytes)
            val progress = (total.toDouble() / expectedBytes)
                .toFloat()
                .coerceIn(0f, 1f)
            onProgress(progress)
        }
    }

    private fun downloadToFile(
        request: Request,
        destination: File,
        append: Boolean = false,
        progressListener: ((Long) -> Unit)? = null
    ) {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(Constants.TAG, "HTTP ${response.code} for ${request.url}")
                throw IOException("Request failed with HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty response body")

            body.byteStream().use { input ->
                FileOutputStream(destination, append).use { output ->
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
        expectedMd5: String?,
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
                if (!fileManager.verifyMd5(destination, expectedMd5)) {
                    throw IOException("MD5 verification failed for ${destination.name}")
                }

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
        fileName: String,
        expectedMd5: String?,
        callback: SteadyFetchCallback
    ) = coroutineScope {
        val tracker = ChunkProgressTracker(chunks)
        val resumeStates = mutableListOf<ChunkResumeState>()
        var initialCompleted = 0
        chunks.forEachIndexed { index, chunk ->
            val expected = chunkSize(chunk)
            val chunkFile = File(downloadDir, chunk.name)
            val existing = if (chunkFile.exists()) {
                chunkFile.length().coerceIn(0L, expected)
            } else {
                0L
            }

            when {
                existing >= expected -> {
                    tracker.seed(index, DownloadStatus.SUCCESS, 1f)
                    resumeStates.add(ChunkResumeState(expected, expected, ChunkResumeState.State.COMPLETE))
                    initialCompleted++
                }
                existing > 0 -> {
                    val progress = (existing.toDouble() / expected).toFloat().coerceIn(0f, 1f)
                    tracker.seed(index, DownloadStatus.QUEUED, progress)
                    resumeStates.add(ChunkResumeState(existing, expected, ChunkResumeState.State.PARTIAL))
                }
                else -> {
                    resumeStates.add(ChunkResumeState(0, expected, ChunkResumeState.State.EMPTY))
                }
            }
        }
        val completionSignal = AtomicBoolean(false)
        val completedChunks = AtomicInteger(initialCompleted)

        Log.d(Constants.TAG, "Chunked download queued ${chunks.size} chunks (resumed $initialCompleted)")
        callback.emitProgress(
            status = DownloadStatus.QUEUED,
            chunkProgress = tracker.snapshot()
        )

        if (initialCompleted == chunks.size) {
            finalizeChunks(downloadDir, fileName, chunks, expectedMd5, tracker.snapshot(), callback, completionSignal)
            return@coroutineScope
        }

        chunks.forEachIndexed { index, chunk ->
            launch {
                val resumeState = resumeStates[index]
                if (resumeState.state == ChunkResumeState.State.COMPLETE) {
                    return@launch
                }

                semaphore.withPermit {
                    if (completionSignal.get()) return@withPermit

                    Log.d(Constants.TAG, "Downloading chunk ${chunk.name}")
                    if (resumeState.state == ChunkResumeState.State.EMPTY) {
                        callback.emitProgress(
                            status = DownloadStatus.RUNNING,
                            chunkProgress = tracker.markRunning(index)
                        )
                    } else {
                        callback.emitProgress(
                            status = DownloadStatus.RUNNING,
                            chunkProgress = tracker.snapshot()
                        )
                    }

                    try {
                        downloadChunkToFile(
                            url = url,
                            headers = headers,
                            downloadDir = downloadDir,
                            chunk = chunk,
                            alreadyDownloaded = resumeState.downloaded
                        ) { progress ->
                            callback.emitProgress(
                                status = DownloadStatus.RUNNING,
                                chunkProgress = tracker.updateProgress(index, progress)
                            )
                        }

                        val snapshot = tracker.markSuccess(index)
                        val finished = completedChunks.incrementAndGet()
                        val allFinished = finished == chunks.size

                        if (allFinished) {
                            finalizeChunks(downloadDir, fileName, chunks, expectedMd5, snapshot, callback, completionSignal)
                        } else {
                            callback.emitProgress(DownloadStatus.RUNNING, snapshot)
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

    private fun finalizeChunks(
        downloadDir: File,
        fileName: String,
        chunks: List<DownloadChunk>,
        expectedMd5: String?,
        snapshot: List<DownloadChunkProgress>,
        callback: SteadyFetchCallback,
        completionSignal: AtomicBoolean
    ) {
        try {
            fileManager.reconcileChunks(downloadDir, fileName, chunks)
            val finalFile = File(downloadDir, fileName)
            val md5Ok = fileManager.verifyMd5(finalFile, expectedMd5)
            if (!md5Ok) {
                throw IOException("MD5 verification failed for $fileName")
            }
            callback.emitProgress(DownloadStatus.SUCCESS, snapshot)
            if (completionSignal.compareAndSet(false, true)) {
                Log.i(Constants.TAG, "All chunks completed with verified checksum")
                callback.onSuccess()
            }
        } catch (mergeError: Exception) {
            Log.e(Constants.TAG, "Failed to finalize download", mergeError)
            callback.emitProgress(
                status = DownloadStatus.FAILED,
                chunkProgress = snapshot
            )
            if (completionSignal.compareAndSet(false, true)) {
                callback.onError(
                    DownloadError(
                        code = -1,
                        message = mergeError.message ?: "Failed to finalize download"
                    )
                )
            }
        }
    }

private fun chunkSize(chunk: DownloadChunk): Long =
    (chunk.end - chunk.start + 1).coerceAtLeast(1L)

private data class ChunkResumeState(
    val downloaded: Long,
    val expected: Long,
    val state: State
) {
    enum class State { EMPTY, PARTIAL, COMPLETE }
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

    private fun tryRangeProbe(
        url: String,
        headers: Map<String, String>
    ): RemoteMetadata? {
        val request = buildRequest(url, headers) {
            addHeader("Range", "bytes=0-0")
        }

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                // Consume the body to allow connection reuse
                response.body?.close()
                
                val contentLength = when (response.code) {
                    206 -> parseContentRangeTotal(response.header("Content-Range"))
                        ?: response.header("Content-Length")?.toLongOrNull()
                    else -> response.header("Content-Length")?.toLongOrNull()
                }

                val supportsRanges = response.code == 206 && response.header("Content-Range") != null
                Log.d(
                    Constants.TAG,
                    "Range probe for $url -> supports=$supportsRanges length=$contentLength status=${response.code}"
                )
                val contentMd5 = response.header("Content-MD5")
                RemoteMetadata(contentLength, supportsRanges, contentMd5)
            }
        } catch (e: IOException) {
            Log.w(
                Constants.TAG,
                "Range probe failed for $url (${e.message ?: "unknown error"})"
            )
            null
        }
    }

    private fun tryHeadForSize(
        url: String,
        headers: Map<String, String>
    ): RemoteMetadata? {
        val request = buildRequest(url, headers) {
            head()
        }

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                response.body?.close()
                val size = response.header("Content-Length")?.toLongOrNull()
                val md5 = response.header("Content-MD5")
                Log.d(Constants.TAG, "HEAD fallback content-length for $url -> $size md5=$md5")
                RemoteMetadata(
                    contentLength = size,
                    supportsRanges = false,
                    contentMd5 = md5
                )
            }
        } catch (e: IOException) {
            Log.w(
                Constants.TAG,
                "HEAD fallback failed for $url (${e.message ?: "unknown error"})"
            )
            null
        }
    }

    private fun parseContentRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val parts = header.split('/')
        if (parts.size != 2) return null
        return parts[1].trim().toLongOrNull()
    }
}
