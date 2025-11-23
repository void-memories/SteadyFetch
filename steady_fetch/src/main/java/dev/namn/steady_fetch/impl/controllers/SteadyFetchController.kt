package dev.namn.steady_fetch.impl.controllers

import android.os.SystemClock
import android.util.Log
import dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback
import dev.namn.steady_fetch.impl.datamodels.DownloadError
import dev.namn.steady_fetch.impl.datamodels.DownloadMetadata
import dev.namn.steady_fetch.impl.datamodels.DownloadProgress
import dev.namn.steady_fetch.impl.datamodels.DownloadRequest
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import dev.namn.steady_fetch.impl.extensions.convertToDownloadError
import dev.namn.steady_fetch.impl.io.Networking
import dev.namn.steady_fetch.impl.managers.ChunkManager
import dev.namn.steady_fetch.impl.managers.FileManager
import dev.namn.steady_fetch.impl.notifications.DownloadNotificationManager
import dev.namn.steady_fetch.impl.utils.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal class SteadyFetchController(
    private val fileManager: FileManager,
    private val networking: Networking,
    private val chunkManager: ChunkManager,
    private val notificationManager: DownloadNotificationManager,
    private val ioScope: CoroutineScope,
) {
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    fun queueDownload(request: DownloadRequest, callback: SteadyFetchCallback): Long {
        val downloadId = nextDownloadId()
        Log.i(Constants.TAG, "queueDownload id=$downloadId url=${request.url}")
        request.validate()

        val decoratedCallback = callback.decorateWithNotifications(downloadId, request.fileName)
        notificationManager.start(downloadId, request.fileName)

        val job = ioScope.launch {
            try {
                val metadata = prepareMetadata(request)
                networking.download(downloadId, metadata, decoratedCallback)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(Constants.TAG, "Failed before download started for $downloadId", throwable)
                decoratedCallback.onError(throwable.convertToDownloadError())
            }
        }

        registerJob(downloadId, job)
        return downloadId
    }

    fun cancel(downloadId: Long): Boolean {
        return try {
            val job = activeJobs.remove(downloadId)
            val jobCancelled = job?.let {
                it.cancel(CancellationException("User cancelled download $downloadId"))
                true
            } ?: false
            val networkCancelled = networking.cancel(downloadId)
            notificationManager.cancel(downloadId)
            jobCancelled || networkCancelled
        } catch (throwable: Throwable) {
            Log.e(Constants.TAG, "Failed to cancel download $downloadId", throwable)
            false
        }
    }

    private fun nextDownloadId(): Long = SystemClock.elapsedRealtimeNanos()

    private fun DownloadRequest.validate() {
        require(maxParallelDownloads in 1..Constants.MAX_PARALLEL_CHUNKS) {
            "maxParallelDownloads must be between 1 and ${Constants.MAX_PARALLEL_CHUNKS}"
        }
    }

    private fun SteadyFetchCallback.decorateWithNotifications(
        downloadId: Long,
        fileName: String
    ): SteadyFetchCallback {
        val delegate = this
        return object : SteadyFetchCallback {
            override fun onSuccess() {
                notificationManager.update(downloadId, fileName, 1f, DownloadStatus.SUCCESS)
                delegate.onSuccess()
            }

            override fun onUpdate(progress: DownloadProgress) {
                notificationManager.update(
                    downloadId,
                    fileName,
                    progress.progress,
                    progress.status
                )
                delegate.onUpdate(progress)
            }

            override fun onError(error: DownloadError) {
                notificationManager.update(downloadId, fileName, 0f, DownloadStatus.FAILED)
                delegate.onError(error)
            }
        }
    }

    private suspend fun prepareMetadata(request: DownloadRequest): DownloadMetadata {
        fileManager.createDirectoryIfNotExists(request.downloadDir)

        val remoteMetadata = networking.fetchRemoteMetadata(request.url, request.headers)
        val contentLength = remoteMetadata.contentLength

        if (contentLength == null) {
            Log.w(Constants.TAG, "Content length unknown, skipping storage validation")
        } else {
            Log.d(Constants.TAG, "Expected file size $contentLength bytes")
            fileManager.validateStorageCapacity(request.downloadDir, contentLength)
        }

        val chunks = if (contentLength != null && remoteMetadata.supportsRanges) {
            chunkManager.generateChunks(request.fileName, contentLength)
        } else {
            if (contentLength != null) {
                Log.i(Constants.TAG, "Chunked transfer not supported, downloading whole file")
            }
            null
        }

        return DownloadMetadata(
            request = request,
            chunks = chunks,
            contentMd5 = remoteMetadata.contentMd5
        )
    }

    private fun registerJob(downloadId: Long, job: Job) {
        activeJobs[downloadId] = job
        job.invokeOnCompletion { cause ->
            activeJobs.remove(downloadId)
            // Only cancel network calls if the job was cancelled
            // On normal completion, calls are already cleaned up in downloadToFile's finally block
            if (cause is CancellationException) {
                networking.cancel(downloadId)
            }
        }
    }
}
