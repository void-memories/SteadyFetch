package dev.namn.steady_fetch.models

import android.app.Application
import android.os.SystemClock

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
    fun queue(request: DownloadRequest): Long? {
        val downloadId = getCurrentTimeInNanos()

    }

    fun query(downloadId: Long): DownloadQueryResponse {}
    suspend fun cancel(downloadId: Long): Boolean {}
    fun resumeInterruptedDownload() {}

    private fun getCurrentTimeInNanos() = SystemClock.elapsedRealtimeNanos()

}
