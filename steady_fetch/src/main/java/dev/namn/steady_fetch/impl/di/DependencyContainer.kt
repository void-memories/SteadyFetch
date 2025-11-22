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
import okhttp3.OkHttpClient

internal class DependencyContainer private constructor(
    private val application: Application
) {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Constants.DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(
                Constants.DEFAULT_READ_TIMEOUT_SECONDS * 2,
                TimeUnit.SECONDS
            )
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

