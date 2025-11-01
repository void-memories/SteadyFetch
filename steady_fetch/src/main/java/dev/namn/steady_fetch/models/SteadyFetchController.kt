package dev.namn.steady_fetch.models

import android.app.Application
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
    companion object {
        private const val TAG = "SteadyFetchController"
    }

    private val downloader = NetworkDownloader()
    private val activeRequests = ConcurrentHashMap<Long, DownloadRequest>()
    private val downloadJobs = ConcurrentHashMap<Long, Job>()
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun queue(request: DownloadRequest): Long? {
        val downloadId = getCurrentTimeInNanos()

        if (request.maxParallelChunks > 30) {
            throw Exception("maxParallelChunks must not exceed 30")
        }

        Log.d(
            TAG,
            "Queueing download ${request.fileName} with max ${request.maxParallelChunks} parallel chunks"
        )

        activeRequests[downloadId] = request.copy()

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

        val chunkMetadata = request.chunks.orEmpty()
        if (chunkMetadata.isEmpty()) {
            return DownloadQueryResponse(
                status = DownloadStatus.QUEUED,
                error = null,
                chunks = emptyList(),
                progress = 0f
            )
        }

        val progressSnapshot = downloader.getDownloadProgressSnapshot(downloadId)
        val chunkProgressList = chunkMetadata.map { chunk ->
            progressSnapshot[chunk.name]
                ?: DownloadChunkWithProgress(
                    chunk = chunk,
                    progress = 0f,
                    downloadedBytes = 0L
                )
        }

        val overallProgress = calculateOverallProgress(chunkProgressList)
        val status = inferStatus(chunkProgressList, overallProgress)

        return DownloadQueryResponse(
            status = status,
            error = null,
            chunks = chunkProgressList,
            progress = overallProgress
        )
    }

    suspend fun cancel(downloadId: Long): Boolean {
        val job = downloadJobs.remove(downloadId)
        val request = activeRequests.remove(downloadId)
        if (job == null) {
            return false
        }

        job.cancel()
        job.cancelAndJoin()
        downloader.clearProgress(downloadId)

        Log.i(TAG, "Cancelled download $downloadId")
        return request != null
    }

    fun resumeInterruptedDownload() {}

    private fun getCurrentTimeInNanos() = SystemClock.elapsedRealtimeNanos()

    private fun validateStorageCapacity(destinationDir: File, expectedBytes: Long?) {
        if (expectedBytes == null) {
            Log.i(TAG, "Storage check skipped: content length unknown")
            return
        }

        val availableBytes = StatFs(destinationDir.absolutePath).availableBytes
        val requiredBytes = (expectedBytes * 1.1).toLong() // includes safety margin

        if (availableBytes < requiredBytes) {
            val message = "Insufficient storage space. " +
                    "Required: ${formatByteCount(requiredBytes)}, " +
                    "Available: ${formatByteCount(availableBytes)}"
            Log.e(TAG, message)
            throw IllegalStateException(message)
        }

        Log.d(
            TAG,
            "Storage check passed. Available: ${formatByteCount(availableBytes)}, " +
                    "Required: ${formatByteCount(requiredBytes)}"
        )
    }

    private fun prepareOutputDirectory(directory: File) {
        val alreadyExists = directory.exists()
        if (!alreadyExists && !directory.mkdirs()) {
            val message = "Unable to create download directory: ${directory.absolutePath}"
            Log.e(TAG, message)
            throw IllegalStateException(message)
        }

        if (!directory.exists()) {
            val message =
                "Download directory unavailable after creation attempt: ${directory.absolutePath}"
            Log.e(TAG, message)
            throw IllegalStateException(message)
        }

        if (!directory.isDirectory) {
            val message = "Download path is not a directory: ${directory.absolutePath}"
            Log.e(TAG, message)
            throw IllegalStateException(message)
        }

        if (!alreadyExists) {
            Log.d(TAG, "Created download directory at ${directory.absolutePath}")
        }
    }

    internal fun calculateChunkRanges(
        totalBytes: Long?,
        preferredChunkSizeBytes: Long?
    ): List<LongRange>? {
        val bytes: Long = totalBytes ?: run {
            Log.i(TAG, "Chunk calculation skipped: content length unknown")
            return null
        }

        require(bytes > 0) { "totalBytes must be > 0" }

        val preferredSize: Long? = preferredChunkSizeBytes
        val desiredChunkSize = if (preferredSize != null && preferredSize >= 1L) preferredSize else DEFAULT_CHUNK_SIZE_BYTES
        val chunkSizeLong = desiredChunkSize

        val calculatedChunkCountRaw = bytes / chunkSizeLong
        val hasRemainder = bytes % chunkSizeLong != 0L
        val calculatedChunkCount = (calculatedChunkCountRaw + if (hasRemainder) 1L else 0L)
            .coerceAtLeast(1L)
        val chunkCount = calculatedChunkCount.toInt()
        val chunkCountLong = chunkCount.toLong()

        val ranges = mutableListOf<LongRange>()
        var start = 0L
        for (i in 0 until chunkCount) {
            val endExclusive = (start + chunkSizeLong).coerceAtMost(bytes)
            val endInclusive = endExclusive - 1
            ranges += start..endInclusive
            start = endExclusive
        }

        return ranges
    }

    private fun createChunkMetadata(
        downloadId: Long,
        request: DownloadRequest,
        totalBytes: Long?,
        chunkRanges: List<LongRange>?
    ): List<DownloadChunk> {
        if (chunkRanges.isNullOrEmpty()) {
            val chunkName = generateChunkNames(request.fileName, 1).first()
            return listOf(
                DownloadChunk(
                    downloadId = downloadId,
                    name = chunkName,
                    chunkIndex = 0,
                    totalBytes = totalBytes,
                    startRange = null,
                    endRange = null
                )
            )
        }

        val partNames = generateChunkNames(request.fileName, chunkRanges.size)
        return partNames.mapIndexed { index, name ->
            val range = chunkRanges[index]
            DownloadChunk(
                downloadId = downloadId,
                name = name,
                chunkIndex = index,
                totalBytes = totalBytes,
                startRange = range.first,
                endRange = range.last
            )
        }
    }

    private fun inferStatus(
        chunks: List<DownloadChunkWithProgress>,
        overallProgress: Float
    ): DownloadStatus {
        if (chunks.isEmpty()) return DownloadStatus.QUEUED

        val allComplete = chunks.all { isChunkComplete(it) }
        if (allComplete && overallProgress >= 1f) return DownloadStatus.SUCCESS

        val anyStarted = chunks.any { it.downloadedBytes > 0L || it.progress > 0f }
        return if (anyStarted) DownloadStatus.RUNNING else DownloadStatus.QUEUED
    }

    private fun launchDownloadJob(downloadId: Long, request: DownloadRequest) {
        val parallelism = request.maxParallelChunks.coerceAtLeast(1)
        val job = downloadScope.launch {
            try {
                val preparedRequest = prepareDownload(downloadId, request)
                downloader.startDownload(downloadId, preparedRequest, parallelism)
                Log.d(TAG, "Download $downloadId completed")
            } catch (cancelled: CancellationException) {
                Log.i(TAG, "Download $downloadId cancelled")
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Download $downloadId failed", error)
            }
        }

        downloadJobs[downloadId]?.cancel()
        downloadJobs[downloadId] = job

        job.invokeOnCompletion {
            downloadJobs.remove(downloadId)
            downloader.clearProgress(downloadId)
        }
    }

    private suspend fun prepareDownload(
        downloadId: Long,
        request: DownloadRequest
    ): DownloadRequest {
        prepareOutputDirectory(request.outputDir)

        val totalBytes = downloader.getTotalBytesOfTheFile(request.url, request.headers)
        validateStorageCapacity(request.outputDir, totalBytes)

        val chunkRanges = calculateChunkRanges(totalBytes, request.preferredChunkSizeBytes)
        val preparedChunks = createChunkMetadata(
            downloadId = downloadId,
            request = request,
            totalBytes = totalBytes,
            chunkRanges = chunkRanges
        )

        val preparedRequest = request.copy(chunks = preparedChunks)
        activeRequests[downloadId] = preparedRequest

        Log.d(
            TAG,
            "Download $downloadId prepared with ${preparedChunks.size} chunk(s); expected size = ${totalBytes ?: "unknown"}"
        )

        return preparedRequest
    }
}
