package dev.namn.steady_fetch.core

import android.app.Application
import android.os.SystemClock
import android.util.Log
import dev.namn.steady_fetch.DownloadChunkWithProgress
import dev.namn.steady_fetch.DownloadError
import dev.namn.steady_fetch.DownloadQueryResponse
import dev.namn.steady_fetch.DownloadRequest
import dev.namn.steady_fetch.DownloadStatus
import dev.namn.steady_fetch.managers.ChunkManager
import dev.namn.steady_fetch.network.NetworkDownloader
import dev.namn.steady_fetch.progress.DownloadProgressStore
import dev.namn.steady_fetch.managers.FileManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * 1. store current nanos = downloadId
 * 2. get total bytes to download
 * 2. check free space on device
 * 3. calculate list of start and end range headers on the basis of chunks
 * 4. calculate file names of all the chunks
 * 5. launch coroutines
 * 6. call okhttp wrapper class for chunked downloading
 * 7. reconsilation of the parts
 * 8. md5 verification
 * 9. if true: delete parts
 *    else: delete parts + reconsilation
 */
internal class SteadyFetchController(private val application: Application) {
    private val progressStore = DownloadProgressStore()
    private val fileManager = FileManager(progressStore)
    private val downloader = NetworkDownloader(progressStore = progressStore, fileManager = fileManager)
    private val activeRequests = ConcurrentHashMap<Long, DownloadRequest>()
    private val downloadJobs = ConcurrentHashMap<Long, Job>()
    private val downloadStatuses = ConcurrentHashMap<Long, DownloadStatus>()
    private val downloadErrors = ConcurrentHashMap<Long, DownloadError?>()
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun queue(request: DownloadRequest): Long? {
        val downloadId = SystemClock.elapsedRealtimeNanos()

        if (request.maxParallelChunks > 30) {
            throw Exception("maxParallelChunks must not exceed 30")
        }

        Log.d(
            TAG,
            "Queueing download ${request.fileName} with max ${request.maxParallelChunks} parallel chunks"
        )

        registerStatusChange(downloadId, DownloadStatus.QUEUED, request.copy())
        launchDownloadJob(downloadId, request)

        return downloadId
    }

    fun query(downloadId: Long): DownloadQueryResponse {
        val request = activeRequests[downloadId]
            ?: return DownloadQueryResponse(
                status = DownloadStatus.FAILED,
                error = DownloadError(404, "Download $downloadId not found"),
                chunks = emptyList(),
                progress = 0f
            )

        val statusOverride = downloadStatuses[downloadId]
        val recordedError = downloadErrors[downloadId]

        val chunkMetadata = request.chunks.orEmpty()
        if (chunkMetadata.isEmpty()) {
            return DownloadQueryResponse(
                status = statusOverride ?: DownloadStatus.QUEUED,
                error = recordedError,
                chunks = emptyList(),
                progress = 0f
            )
        }

        val progressSnapshot = downloader.getDownloadProgressSnapshot(downloadId)
        val chunkProgressList = chunkMetadata.map { chunk ->
            progressSnapshot[chunk.name]
                ?: run {
                    val expectedBytes = ChunkManager.expectedBytesForChunk(chunk)
                    val downloadedBytes = when (statusOverride) {
                        DownloadStatus.SUCCESS -> expectedBytes ?: chunk.totalBytes ?: 0L
                        else -> 0L
                    }
                    val progress = when (statusOverride) {
                        DownloadStatus.SUCCESS -> 1f
                        else -> 0f
                    }

                    DownloadChunkWithProgress(
                        chunk = chunk,
                        progress = progress,
                        downloadedBytes = downloadedBytes
                    )
                }
        }

        val overallProgress = when (statusOverride) {
            DownloadStatus.SUCCESS -> 1f
            else -> ChunkManager.overallProgress(chunkProgressList)
        }

        val status = statusOverride ?: inferStatus(chunkProgressList, overallProgress)

        return DownloadQueryResponse(
            status = status,
            error = recordedError,
            chunks = chunkProgressList,
            progress = overallProgress
        )
    }

    suspend fun cancel(downloadId: Long): Boolean {
        val job = downloadJobs.remove(downloadId)
        val request = activeRequests[downloadId]
        if (job == null) {
            return false
        }

        downloadStatuses[downloadId] = DownloadStatus.FAILED
        downloadErrors[downloadId] = DownloadError(499, "Download $downloadId cancelled by user")

        job.cancel()
        job.cancelAndJoin()
        downloader.clearProgress(downloadId)

        Log.i(TAG, "Cancelled download $downloadId")
        return request != null
    }

    fun resumeInterruptedDownload() {}

    //todo: func looks hella sus to be w/ unused vars
    internal fun calculateChunkRanges(
        totalBytes: Long?,
        preferredChunkSizeBytes: Long?
    ): List<LongRange>? = ChunkManager.calculateRanges(totalBytes, preferredChunkSizeBytes)


    private suspend fun prepareDownload(downloadId: Long, request: DownloadRequest): PreparedDownload {
        fileManager.prepareDirectory(request.outputDir)

        val totalBytes = downloader.getTotalBytesOfTheFile(request.url, request.headers)
        fileManager.ensureCapacity(request.outputDir, totalBytes)

        val chunkRanges = ChunkManager.calculateRanges(totalBytes, request.preferredChunkSizeBytes)
        val preparedChunks = ChunkManager.createMetadata(
            downloadId = downloadId,
            request = request,
            totalBytes = totalBytes,
            chunkRanges = chunkRanges
        )

        val preparedRequest = request.copy(chunks = preparedChunks)

        return PreparedDownload(
            request = preparedRequest,
            totalBytes = totalBytes
        )
    }

    private fun inferStatus(
        chunks: List<DownloadChunkWithProgress>,
        overallProgress: Float
    ): DownloadStatus {
        if (chunks.isEmpty()) return DownloadStatus.QUEUED

        val allComplete = chunks.all { ChunkManager.isChunkComplete(it) }
        if (allComplete && overallProgress >= 1f) return DownloadStatus.SUCCESS

        val anyStarted = chunks.any { it.downloadedBytes > 0L || it.progress > 0f }
        return if (anyStarted) DownloadStatus.RUNNING else DownloadStatus.QUEUED
    }

    private fun launchDownloadJob(downloadId: Long, request: DownloadRequest) {
        val parallelism = request.maxParallelChunks.coerceAtLeast(1)
        val job = downloadScope.launch {
            try {
                val preparationResult = prepareDownload(downloadId, request)
                val preparedRequest = preparationResult.request
                activeRequests[downloadId] = preparedRequest
                Log.d(
                    TAG,
                    "Download $downloadId prepared with ${preparedRequest.chunks?.size ?: 0} chunk(s); expected size = ${preparationResult.totalBytes ?: "unknown"}"
                )
                registerStatusChange(downloadId, DownloadStatus.RUNNING)
                downloader.startDownload(downloadId, preparedRequest, parallelism)
                registerStatusChange(downloadId, DownloadStatus.SUCCESS)
            } catch (error: Exception) {
                registerStatusChange(downloadId, DownloadStatus.FAILED, null, error)
            }
        }

        //todo: is this cancelling the scope asap?
        downloadJobs[downloadId]?.cancel()
        downloadJobs[downloadId] = job

        job.invokeOnCompletion {
            downloadJobs.remove(downloadId)
        }
    }

    private fun registerStatusChange(
        downloadId: Long, downloadStatus: DownloadStatus, request: DownloadRequest? = null, error:
        Exception? = null
    ) {
        when (downloadStatus) {
            DownloadStatus.QUEUED -> {
                activeRequests[downloadId] = request!!
                downloadStatuses[downloadId] = DownloadStatus.QUEUED
                downloadErrors.remove(downloadId)
            }

            DownloadStatus.RUNNING -> {
                downloadStatuses[downloadId] = DownloadStatus.RUNNING
                downloadErrors.remove(downloadId)

            }

            DownloadStatus.SUCCESS -> {
                downloadStatuses[downloadId] = DownloadStatus.SUCCESS
                downloadErrors.remove(downloadId)
            }

            DownloadStatus.FAILED -> {
                downloadStatuses[downloadId] = DownloadStatus.FAILED
                downloadErrors[downloadId] = error!!.toDownloadError()
            }
        }
    }
    companion object {
        private const val TAG = "SteadyFetchController"
        private val HTTP_CODE_REGEX = Regex("HTTP\\s+(\\d{3})")

        private fun mapToDownloadError(throwable: Throwable): DownloadError {
            if (throwable is CancellationException) {
                return DownloadError(499, "Download cancelled")
            }

            val message = throwable.message?.takeUnless { it.isBlank() }
                ?: throwable::class.java.simpleName
            val httpCode = extractHttpCode(message)

            val code = when {
                httpCode != null -> httpCode
                throwable is IllegalArgumentException || throwable is IllegalStateException -> 400
                else -> 500
            }

            return DownloadError(code, message)
        }

        private fun extractHttpCode(message: String?): Int? {
            if (message.isNullOrBlank()) return null
            val match = HTTP_CODE_REGEX.find(message)
            return match?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    }
}

private fun Throwable.toDownloadError(): DownloadError {
    if (this is CancellationException) {
        return DownloadError(499, "Download cancelled")
    }

    val message = this.message?.takeUnless { it.isBlank() }
        ?: this::class.java.simpleName
    val httpCode = extractHttpCode(message)

    val code = when {
        httpCode != null -> httpCode
        this is IllegalArgumentException || this is IllegalStateException -> 400
        else -> 500
    }

    return DownloadError(code, message)
}

private fun extractHttpCode(message: String?): Int? {
    if (message.isNullOrBlank()) return null
    val match = Regex("HTTP\\s+(\\d{3})").find(message)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull()
}

private data class PreparedDownload(
    val request: DownloadRequest,
    val totalBytes: Long?
)
