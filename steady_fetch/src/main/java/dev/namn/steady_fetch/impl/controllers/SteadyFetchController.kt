package dev.namn.steady_fetch.impl.controllers

import ChunkManager
import android.app.Application
import android.os.SystemClock
import android.util.Log
import dev.namn.steady_fetch.impl.uilts.Constants
import dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback
import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import dev.namn.steady_fetch.impl.datamodels.DownloadMetadata
import dev.namn.steady_fetch.impl.datamodels.DownloadProgress
import dev.namn.steady_fetch.impl.datamodels.DownloadRequest
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import dev.namn.steady_fetch.impl.datamodels.DownloadError
import dev.namn.steady_fetch.impl.io.Networking
import dev.namn.steady_fetch.impl.managers.FileManager
import dev.namn.steady_fetch.impl.notifications.DownloadNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

internal class SteadyFetchController(private val application: Application) {
    private val okHttpClient = OkHttpClient()
    private val fileManager = FileManager()
    private val networking = Networking(okHttpClient, fileManager)
    private val chunkManager = ChunkManager()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager = DownloadNotificationManager(application)

    fun queueDownload(request: DownloadRequest, callback: SteadyFetchCallback): Long {
        val downloadId = SystemClock.elapsedRealtimeNanos()
        Log.i(Constants.TAG, "queueDownload id=$downloadId url=${request.url}")

        if (request.maxParallelDownloads > Constants.MAX_PARALLEL_CHUNKS) {
            Log.e(Constants.TAG, "maxParallelDownloads exceeded: ${request.maxParallelDownloads}")
            throw Exception("maxParallelDownloads must not exceed ${Constants.MAX_PARALLEL_CHUNKS}")
        }

        val decoratedCallback = object : SteadyFetchCallback {
            override fun onSuccess() {
                notificationManager.update(
                    downloadId,
                    request.fileName,
                    1f,
                    DownloadStatus.SUCCESS
                )
                callback.onSuccess()
            }

            override fun onUpdate(progress: DownloadProgress) {
                notificationManager.update(
                    downloadId,
                    request.fileName,
                    progress.progress,
                    progress.status
                )
                callback.onUpdate(progress)
            }

            override fun onError(error: DownloadError) {
                notificationManager.update(
                    downloadId,
                    request.fileName,
                    0f,
                    DownloadStatus.FAILED
                )
                callback.onError(error)
            }
        }

        notificationManager.start(downloadId, request.fileName)

        ioScope.launch {
            fileManager.createDirectoryIfNotExists(request.downloadDir)

            val remoteMetadata = networking.fetchRemoteMetadata(request.url, request.headers)
            val expectedFileSize = remoteMetadata.contentLength
            var chunks: List<DownloadChunk>? = null

            if (expectedFileSize == null) {
                Log.w(Constants.TAG, "Content length unknown, skipping storage validation")
            } else {
                Log.d(Constants.TAG, "Expected file size $expectedFileSize bytes")
                fileManager.validateStorageCapacity(request.downloadDir, expectedFileSize)
                if (remoteMetadata.supportsRanges) {
                    chunks = chunkManager.generateChunks(request.fileName, expectedFileSize)
                } else {
                    Log.i(Constants.TAG, "Chunked transfer not supported, downloading whole file")
                }
            }

            val metadata = DownloadMetadata(
                request = request,
                chunks = chunks,
                contentMd5 = remoteMetadata.contentMd5
            )

            networking.download(metadata, decoratedCallback)
        }
        return downloadId
    }
}
