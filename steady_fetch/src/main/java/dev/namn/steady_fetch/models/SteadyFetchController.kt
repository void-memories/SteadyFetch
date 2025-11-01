package dev.namn.steady_fetch.models

import android.app.Application
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import java.io.File

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

    fun queue(request: DownloadRequest): Long? {
        val downloadId = getCurrentTimeInNanos()
        Log.d(
            TAG,
            "Queueing download ${request.fileName} with max ${request.maxParallelChunks} parallel chunks"
        )

        prepareOutputDirectory(request.outputDir)

        val totalBytes = downloader.getTotalBytesOfTheFile(request.url, request.headers)
        validateStorageCapacity(request.outputDir, totalBytes)

        val chunkRanges = calculateChunkRanges(totalBytes, request.maxParallelChunks)
        request.chunks = createChunkMetadata(
            downloadId = downloadId,
            request = request,
            totalBytes = totalBytes,
            chunkRanges = chunkRanges
        )

        Log.d(
            TAG,
            "Download $downloadId prepared with ${request.chunks?.size ?: 0} chunk(s); expected size = ${totalBytes ?: "unknown"}"
        )

        return downloadId
    }

    fun query(downloadId: Long): DownloadQueryResponse =
        DownloadQueryResponse(
            status = DownloadStatus.QUEUED,
            error = null,
            chunks = emptyList(),
            progress = 0f
        )

    suspend fun cancel(downloadId: Long): Boolean = false

    fun resumeInterruptedDownload() {}

    private fun generateChunkNames(name: String, numChunks: Int): List<String> {
        require(numChunks > 0) { "numChunks must be > 0" }
        require(name.isNotBlank()) { "name must not be blank" }

        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0 && dot < name.length - 1) {
            name.substring(0, dot) to name.substring(dot)
        } else {
            name to ""
        }

        val width = numChunks.toString().length
        return (1..numChunks).map { idx ->
            "%s.part%0${width}d-of-%0${width}d%s".format(base, idx, numChunks, ext)
        }
    }


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

    private fun formatByteCount(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.2f MB", mb)
            kb >= 1.0 -> String.format("%.2f KB", kb)
            else -> "$bytes bytes"
        }
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
        requestedChunks: Int,
        maxSupportedChunks: Int = 8
    ): List<LongRange>? {
        totalBytes ?: run {
            Log.i(TAG, "Chunk calculation skipped: content length unknown")
            return null
        }

        require(totalBytes > 0) { "totalBytes must be > 0" }
        require(requestedChunks > 0) { "maxChunks must be > 0" }

        val chunksDesired = minOf(maxSupportedChunks, requestedChunks)
        val effectiveChunks = minOf(chunksDesired.toLong(), totalBytes).toInt()
        val baseSize = totalBytes / effectiveChunks
        val remainder = totalBytes % effectiveChunks

        var offset = 0L
        return List(effectiveChunks) { index ->
            val extraByte = if (index < remainder) 1 else 0
            val chunkSize = baseSize + extraByte
            val endInclusive = offset + chunkSize - 1
            val range = offset..endInclusive
            offset = endInclusive + 1
            range
        }
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
}
