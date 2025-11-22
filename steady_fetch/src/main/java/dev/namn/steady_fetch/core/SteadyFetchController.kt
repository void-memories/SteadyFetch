package dev.namn.steady_fetch.core

import android.app.Application
import android.os.SystemClock
import android.util.Log
import dev.namn.steady_fetch.Constants
import dev.namn.steady_fetch.datamodels.DownloadChunkWithProgress
import dev.namn.steady_fetch.datamodels.DownloadError
import dev.namn.steady_fetch.datamodels.DownloadQueryResponse
import dev.namn.steady_fetch.datamodels.DownloadRequest
import dev.namn.steady_fetch.datamodels.DownloadStatus
import dev.namn.steady_fetch.datamodels.PreparedDownload
import dev.namn.steady_fetch.util.convertToDownloadError
import dev.namn.steady_fetch.managers.ChunkManager
import dev.namn.steady_fetch.network.NetworkDownloader
import dev.namn.steady_fetch.progress.DownloadProgressStore
import dev.namn.steady_fetch.managers.FileManager
import java.util.concurrent.ConcurrentHashMap
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

    fun queueDownload(request: DownloadRequest): Long? {
        val downloadId = SystemClock.elapsedRealtimeNanos()

        if (request.maxParallelChunks > Constants.MAX_PARALLEL_CHUNKS) {
            throw Exception("maxParallelChunks must not exceed ${Constants.MAX_PARALLEL_CHUNKS}")
        }

        Log.d(
            Constants.TAG_STEADY_FETCH_CONTROLLER,
            "Queueing download ${request.fileName} with max ${request.maxParallelChunks} parallel chunks"
        )

        updateDownloadStatus(downloadId, DownloadStatus.QUEUED, request.copy())
        launchDownloadCoroutine(downloadId, request)

        return downloadId
    }

    fun queryDownloadStatus(downloadId: Long): DownloadQueryResponse {
        val request = activeRequests[downloadId]
            ?: return DownloadQueryResponse(
                status = DownloadStatus.FAILED,
                error = DownloadError(Constants.ERROR_CODE_NOT_FOUND, "Download $downloadId not found"),
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

        val progressSnapshot = downloader.getProgressSnapshot(downloadId)
        val chunkProgressList = chunkMetadata.map { chunk ->
            progressSnapshot[chunk.name]
                ?: run {
                    val expectedBytes = ChunkManager.calculateExpectedBytesForChunk(chunk)
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
            else -> ChunkManager.calculateOverallDownloadProgress(chunkProgressList)
        }

        val status = statusOverride ?: inferDownloadStatus(chunkProgressList, overallProgress)

        return DownloadQueryResponse(
            status = status,
            error = recordedError,
            chunks = chunkProgressList,
            progress = overallProgress
        )
    }

    suspend fun cancelDownload(downloadId: Long): Boolean {
        val job = downloadJobs.remove(downloadId)
        val request = activeRequests[downloadId]
        if (job == null) {
            return false
        }

        downloadStatuses[downloadId] = DownloadStatus.FAILED
        downloadErrors[downloadId] = DownloadError(Constants.ERROR_CODE_CANCELLED, "Download $downloadId cancelled by user")

        job.cancel()
        job.cancelAndJoin()
        downloader.clearDownloadProgress(downloadId)

        Log.i(Constants.TAG_STEADY_FETCH_CONTROLLER, "Cancelled download $downloadId")
        return request != null
    }

    fun resumeInterruptedDownload() {}

    //todo: func looks hella sus to be w/ unused vars
    internal fun calculateDownloadChunkRanges(
        totalBytes: Long?,
        preferredChunkSizeBytes: Long?
    ): List<LongRange>? = ChunkManager.calculateChunkByteRanges(totalBytes, preferredChunkSizeBytes)


    private suspend fun prepareDownloadRequest(downloadId: Long, request: DownloadRequest): PreparedDownload {
        fileManager.ensureDirExists(request.outputDir)

        val totalBytes = downloader.fetchFileContentLength(request.url, request.headers)
        fileManager.validateStorageCapacity(request.outputDir, totalBytes)

        val chunkRanges = ChunkManager.calculateChunkByteRanges(totalBytes, request.preferredChunkSizeBytes)
        val preparedChunks = ChunkManager.createChunkMetadata(
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

    private fun inferDownloadStatus(
        chunks: List<DownloadChunkWithProgress>,
        overallProgress: Float
    ): DownloadStatus {
        if (chunks.isEmpty()) return DownloadStatus.QUEUED

        val allComplete = chunks.all { ChunkManager.isChunkDownloadComplete(it) }
        if (allComplete && overallProgress >= 1f) return DownloadStatus.SUCCESS

        val anyStarted = chunks.any { it.downloadedBytes > 0L || it.progress > 0f }
        return if (anyStarted) DownloadStatus.RUNNING else DownloadStatus.QUEUED
    }

    private fun launchDownloadCoroutine(downloadId: Long, request: DownloadRequest) {
        val parallelism = request.maxParallelChunks.coerceAtLeast(1)
        val job = downloadScope.launch {
            val preparationResult = prepareDownloadRequest(downloadId, request)
            val preparedRequest = preparationResult.request
            activeRequests[downloadId] = preparedRequest
            Log.d(
                Constants.TAG_STEADY_FETCH_CONTROLLER,
                "Download $downloadId prepared with ${preparedRequest.chunks?.size ?: 0} chunk(s); expected size = ${preparationResult.totalBytes ?: "unknown"}"
            )
            updateDownloadStatus(downloadId, DownloadStatus.RUNNING)
            downloader.executeDownload(downloadId, preparedRequest, parallelism)
            updateDownloadStatus(downloadId, DownloadStatus.SUCCESS)
        }

        downloadJobs[downloadId]?.cancel()
        downloadJobs[downloadId] = job

        job.invokeOnCompletion { throwable ->
            downloadJobs.remove(downloadId)
            if (throwable != null) {
                updateDownloadStatus(downloadId, DownloadStatus.FAILED, error = throwable as? Exception)
            }
        }
    }

    private fun updateDownloadStatus(
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
                downloadErrors[downloadId] = error!!.convertToDownloadError()
            }
        }
    }
}
