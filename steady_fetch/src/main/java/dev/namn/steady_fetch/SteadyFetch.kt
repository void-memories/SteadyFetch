package dev.namn.steady_fetch

import android.app.Application
import dev.namn.steady_fetch.core.SteadyFetchController
import dev.namn.steady_fetch.datamodels.DownloadQueryResponse
import dev.namn.steady_fetch.datamodels.DownloadRequest
import java.util.concurrent.atomic.AtomicBoolean

object SteadyFetch {
    private var steadyFetchController: SteadyFetchController? = null
    private val isInitialized = AtomicBoolean(false)

    @Synchronized
    fun init(application: Application) {
        try {
            steadyFetchController = SteadyFetchController(application)
            isInitialized.set(true)
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize SteadyFetch", e)
        }
    }

    fun queue(request: DownloadRequest): Long? {
        try {
            initCheck()
            return steadyFetchController!!.queue(request)
        } catch (e: Exception) {
            throw RuntimeException("Failed to queue download: ${e.message}", e)
        }
    }

    fun query(downloadId: Long): DownloadQueryResponse {
        try {
            initCheck()
            return steadyFetchController!!.query(downloadId)
        } catch (e: Exception) {
            throw RuntimeException("Failed to query download: ${e.message}", e)
        }
    }

    suspend fun cancel(downloadId: Long): Boolean {
        try {
            initCheck()
            return steadyFetchController!!.cancel(downloadId)
        } catch (e: Exception) {
            throw RuntimeException("Failed to cancel download: ${e.message}", e)
        }
    }

    private fun initCheck() {
        if (!isInitialized.get()) throw Exception("SteadyFetch SDK not initialized")
    }
}
