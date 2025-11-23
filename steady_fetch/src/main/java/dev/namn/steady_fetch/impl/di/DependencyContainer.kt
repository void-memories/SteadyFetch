package dev.namn.steady_fetch.impl.di

import android.app.Application
import dev.namn.steady_fetch.impl.controllers.SteadyFetchController
import dev.namn.steady_fetch.impl.io.Networking
import dev.namn.steady_fetch.impl.managers.ChunkManager
import dev.namn.steady_fetch.impl.managers.FileManager
import dev.namn.steady_fetch.impl.notifications.DownloadNotificationManager
import dev.namn.steady_fetch.impl.utils.Constants
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

internal class DependencyContainer private constructor(
    private val application: Application
) {
    private val okHttpClient: OkHttpClient by lazy {
        // Connection pool: max 50 idle connections, keep alive for 5 minutes
        // This allows many concurrent chunk downloads across multiple files
        val connectionPool = ConnectionPool(50, 5, TimeUnit.MINUTES)
        
        OkHttpClient.Builder()
            .connectTimeout(Constants.DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Very generous read timeout for large chunks and slow connections
            // For 8MB chunk: at 0.1MB/s = 80s, at 0.05MB/s = 160s
            // 10 minutes provides ample margin for very slow connections and network hiccups
            .readTimeout(10, TimeUnit.MINUTES)
            // Write timeout for slow uploads (though we're downloading)
            .writeTimeout(5, TimeUnit.MINUTES)
            // Call timeout should be much longer than read timeout to allow for retries
            // 30 minutes allows for multiple retries even on very slow connections
            .callTimeout(30, TimeUnit.MINUTES)
            .connectionPool(connectionPool)
            .retryOnConnectionFailure(true)
            .build()
    }
    private val fileManager: FileManager by lazy { FileManager() }
    private val chunkManager: ChunkManager by lazy { ChunkManager() }
    private val ioScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    private val downloadNotificationManager: DownloadNotificationManager by lazy {
        DownloadNotificationManager(application)
    }
    private val networking: Networking by lazy { Networking(okHttpClient, fileManager) }

    private val controller: SteadyFetchController by lazy {
        SteadyFetchController(
            fileManager = fileManager,
            networking = networking,
            chunkManager = chunkManager,
            notificationManager = downloadNotificationManager,
            ioScope = ioScope
        )
    }

    fun getSteadyFetchController(): SteadyFetchController = controller

    companion object {
        @Volatile private var instance: DependencyContainer? = null

        fun getInstance(application: Application): DependencyContainer {
            return instance ?: synchronized(this) {
                instance ?: DependencyContainer(application).also { instance = it }
            }
        }
    }
}

