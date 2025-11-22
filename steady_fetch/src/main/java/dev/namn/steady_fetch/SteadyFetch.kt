package dev.namn.steady_fetch

import android.app.Application
import android.util.Log
import dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback
import dev.namn.steady_fetch.impl.controllers.SteadyFetchController
import dev.namn.steady_fetch.impl.datamodels.DownloadRequest
import dev.namn.steady_fetch.impl.uilts.Constants
import java.util.concurrent.atomic.AtomicBoolean

object SteadyFetch {
    private var steadyFetchController: SteadyFetchController? = null
    private val isInitialized = AtomicBoolean(false)

    @Synchronized
    fun initialize(application: Application) {
        try {
            Log.i(Constants.TAG, "Initializing SteadyFetch")
            steadyFetchController = SteadyFetchController(application)
            isInitialized.set(true)
            Log.i(Constants.TAG, "SteadyFetch initialized")
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Initialization failed", e)
            throw RuntimeException("Failed to initialize SteadyFetch", e)
        }
    }

    fun queueDownload(request: DownloadRequest, callback: SteadyFetchCallback): Long? {
        return try {
            ensureInitialized()
            Log.i(Constants.TAG, "Queue requested for ${request.url}")
            val downloadId = steadyFetchController!!.queueDownload(request, callback)
            Log.i(Constants.TAG, "Download queued id=$downloadId")
            downloadId
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Failed to queue download", e)
            throw RuntimeException("Failed to queue download: ${e.message}", e)
        }
    }

    fun cancelDownload(downloadId: Long): Boolean {
        ensureInitialized()
        return steadyFetchController?.cancel(downloadId) ?: false
    }

    private fun ensureInitialized() {
        if (!isInitialized.get()) {
            Log.e(Constants.TAG, "SteadyFetch SDK not initialized")
            throw Exception("SteadyFetch SDK not initialized")
        }
    }
}
