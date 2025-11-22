package dev.namn.steady_fetch.core

import ChunkManager
import android.app.Application
import android.os.SystemClock
import android.util.Log
import dev.namn.steady_fetch.Constants
import dev.namn.steady_fetch.SteadyFetchCallback
import dev.namn.steady_fetch.datamodels.DownloadChunk
import dev.namn.steady_fetch.datamodels.DownloadMetadata
import dev.namn.steady_fetch.datamodels.DownloadRequest
import dev.namn.steady_fetch.io.Networking
import dev.namn.steady_fetch.managers.FileManager

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
    private val fileManager = FileManager()
    private val networking = Networking()
    private val chunkManager = ChunkManager()

    fun queueDownload(request: DownloadRequest, callback: SteadyFetchCallback): Long {
        val downloadId = SystemClock.elapsedRealtimeNanos()

        if (request.maxParallelDownloads > Constants.MAX_PARALLEL_CHUNKS) {
            throw Exception("maxParallelChunks must not exceed ${Constants.MAX_PARALLEL_CHUNKS}")
        }

        fileManager.createDirectoryIfNotExists(request.downloadDir)

        val expectedFileSize = networking.getExpectedFileSize(request.url, request.headers)
        var chunks: List<DownloadChunk>? = null

        if (expectedFileSize == null) {
            Log.i(Constants.TAG, "Storage check skipped: content length unknown")
        } else {
            fileManager.validateStorageCapacity(request.downloadDir, expectedFileSize)
            if (networking.doesServerSupportChunking(request.url, request.headers)) {
                chunks = chunkManager.generateChunks(request.fileName, expectedFileSize)
            }
        }

        val metadata = DownloadMetadata(
            request = request,
            chunks = chunks
        )

        networking.download(metadata, callback)
        return downloadId
    }
}
