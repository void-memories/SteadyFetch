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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Protocol

internal class SteadyFetchController(private val application: Application) {
    private val okHttpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1)) // Force HTTP/1.1 - some servers have issues with HTTP/2
        .addInterceptor { chain ->
            // Some servers block OkHttp's default User-Agent, use a browser-like one
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .removeHeader("User-Agent")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build()
            chain.proceed(newRequest)
        }
        .build()
    private val fileManager = FileManager()
    private val networking = Networking(okHttpClient)
    private val chunkManager = ChunkManager()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun queueDownload(request: DownloadRequest, callback: SteadyFetchCallback): Long {
        val downloadId = SystemClock.elapsedRealtimeNanos()
        Log.i(Constants.TAG, "queueDownload id=$downloadId url=${request.url}")

        if (request.maxParallelDownloads > Constants.MAX_PARALLEL_CHUNKS) {
            Log.e(Constants.TAG, "maxParallelDownloads exceeded: ${request.maxParallelDownloads}")
            throw Exception("maxParallelDownloads must not exceed ${Constants.MAX_PARALLEL_CHUNKS}")
        }

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
                chunks = chunks
            )

            networking.download(metadata, callback)
        }
        return downloadId
    }
}
