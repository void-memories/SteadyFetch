package dev.namn.steady_fetch.models

import android.app.Application
import android.os.SystemClock
import kotlin.math.min

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
    private val maxSupportedChunks = 16

    fun queue(request: DownloadRequest): Long? {
        val downloadId = getCurrentTimeInNanos()
        val fileBytes = getTotalBytesOfTheFile()

        if (fileBytes == null) throw Exception("File Bytes are null")
        checkInternalStorageSpace()




        return downloadId
    }

    fun query(downloadId: Long): DownloadQueryResponse {
        return DownloadQueryResponse(
            status = DownloadStatus.QUEUED,
            error = null,
            chunks = emptyList(),
            progress = 0f
        )
    }

    suspend fun cancel(downloadId: Long): Boolean {
        return false
    }

    fun resumeInterruptedDownload() {}

    private fun getCurrentTimeInNanos() = SystemClock.elapsedRealtimeNanos()
    private fun getTotalBytesOfTheFile(): Long? {
        return null
    }
    private fun checkInternalStorageSpace() {}
    internal fun divideDownloadIntoChunks(
        totalBytes: Long,
        maxChunks: Int,
        maxSupportedChunks: Int = 8
    ): List<LongRange> {
        require(totalBytes > 0) { "totalBytes must be > 0" }
        require(maxChunks > 0) { "maxChunks must be > 0" }

        val chunksWanted = minOf(maxSupportedChunks, maxChunks)

        // If file is tiny, don't make zero-sized ranges.
        val effectiveChunks = minOf(chunksWanted.toLong(), totalBytes).toInt()

        val base = totalBytes / effectiveChunks
        val remainder = totalBytes % effectiveChunks

        val ranges = ArrayList<LongRange>(effectiveChunks)
        var start = 0L
        for (i in 0 until effectiveChunks) {
            val size = base + if (i < remainder) 1 else 0
            val endInclusive = start + size - 1
            ranges += (start..endInclusive)
            start = endInclusive + 1
        }

        return ranges
    }
}
