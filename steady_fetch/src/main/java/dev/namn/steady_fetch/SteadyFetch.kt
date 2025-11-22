package dev.namn.steady_fetch

import android.app.Application
import dev.namn.steady_fetch.core.SteadyFetchController
import dev.namn.steady_fetch.datamodels.DownloadRequest
import java.util.concurrent.atomic.AtomicBoolean

object SteadyFetch {
    private var steadyFetchController: SteadyFetchController? = null
    private val isInitialized = AtomicBoolean(false)

    @Synchronized
    fun initialize(application: Application) {
        try {
            steadyFetchController = SteadyFetchController(application)
            isInitialized.set(true)
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize SteadyFetch", e)
        }
    }

    fun queueDownload(request: DownloadRequest, callback: SteadyFetchCallback): Long? {
        try {
            ensureInitialized()
            return steadyFetchController!!.queueDownload(request, callback)
        } catch (e: Exception) {
            throw RuntimeException("Failed to queue download: ${e.message}", e)
        }
    }

    suspend fun cancelDownload(downloadId: Long): Boolean {
//        try {
//            ensureInitialized()
//            return steadyFetchController!!.cancelDownload(downloadId)
//        } catch (e: Exception) {
//            throw RuntimeException("Failed to cancel download: ${e.message}", e)
//        }
    }

    private fun ensureInitialized() {
        if (!isInitialized.get()) throw Exception("SteadyFetch SDK not initialized")
    }
}
