package dev.namn.steady_fetch

import android.app.Application
import android.util.Log
import dev.namn.steady_fetch.core.SteadyFetchController
import dev.namn.steady_fetch.datamodels.DownloadRequest
import dev.namn.steady_fetch.uilts.Constants
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

    suspend fun cancelDownload(downloadId: Long): Boolean {
        Log.w(Constants.TAG, "cancelDownload not implemented. id=$downloadId")
        return false
    }

    private fun ensureInitialized() {
        if (!isInitialized.get()) {
            Log.e(Constants.TAG, "SteadyFetch SDK not initialized")
            throw Exception("SteadyFetch SDK not initialized")
        }
    }
}
