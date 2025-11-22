package dev.namn.steady_fetch

import android.app.Application
import android.util.Log
import dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback
import dev.namn.steady_fetch.impl.controllers.SteadyFetchController
import dev.namn.steady_fetch.impl.datamodels.DownloadRequest
import dev.namn.steady_fetch.impl.di.DependencyContainer
import dev.namn.steady_fetch.impl.utils.Constants
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Public entry point for the SteadyFetch download engine.
 *
 * Call [initialize] once at app start, then use [queueDownload] to enqueue work and
 * [cancelDownload] to abort an in-flight transfer.
 */
object SteadyFetch {
    @Suppress("unused")
    private var dependencyContainer: DependencyContainer? = null
    private var steadyFetchController: SteadyFetchController? = null
    private val isInitialized = AtomicBoolean(false)

    @JvmStatic
    @Synchronized
    fun initialize(application: Application) {
        if (isInitialized.get()) {
            Log.w(Constants.TAG, "SteadyFetch already initialized; ignoring duplicate call")
            return
        }

        runCatching {
            Log.i(Constants.TAG, "Initializing SteadyFetch")
            val container = DependencyContainer.getInstance(application)
            dependencyContainer = container
            steadyFetchController = container.getSteadyFetchController()
            isInitialized.set(true)
            Log.i(Constants.TAG, "SteadyFetch initialized")
        }.getOrElse { error ->
            Log.e(Constants.TAG, "Initialization failed", error)
            throw IllegalStateException("Failed to initialize SteadyFetch", error)
        }
    }

    @JvmStatic
    fun queueDownload(request: DownloadRequest, callback: SteadyFetchCallback): Long {
        val controller = ensureInitialized()
        Log.i(Constants.TAG, "Queue requested for ${request.url}")
        return controller.queueDownload(request, callback).also { downloadId ->
            Log.i(Constants.TAG, "Download queued id=$downloadId")
        }
    }

    @JvmStatic
    fun cancelDownload(downloadId: Long): Boolean {
        val controller = ensureInitialized()
        val cancelled = controller.cancel(downloadId)
        if (cancelled) {
            Log.i(Constants.TAG, "Cancelled download $downloadId")
        } else {
            Log.w(Constants.TAG, "No active download found for id=$downloadId")
        }
        return cancelled
    }

    private fun ensureInitialized(): SteadyFetchController {
        return steadyFetchController ?: run {
            Log.e(Constants.TAG, "SteadyFetch SDK not initialized")
            throw IllegalStateException("SteadyFetch SDK not initialized. Call initialize() first.")
        }
    }
}
