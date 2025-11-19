package dev.namn.steady_fetch

import android.app.Application
import dev.namn.steady_fetch.core.SteadyFetchController
import java.util.concurrent.atomic.AtomicBoolean

//TODO: introduce try/catch
object SteadyFetch {
    private var steadyFetchController: SteadyFetchController? = null
    private val isInitialized = AtomicBoolean(false)

    @Synchronized
    fun init(application: Application) {
        steadyFetchController = SteadyFetchController(application)
        isInitialized.set(true)
    }

    fun queue(request: DownloadRequest): Long? {
        initCheck()
        return steadyFetchController!!.queue(request)
    }

    fun query(downloadId: Long): DownloadQueryResponse {
        initCheck()
        return steadyFetchController!!.query(downloadId)
    }

    suspend fun cancel(downloadId: Long): Boolean {
        initCheck()
        return steadyFetchController!!.cancel(downloadId)
    }

    private fun initCheck() {
        if (!isInitialized.get()) throw Exception("SteadyFetch SDK not initialized")
    }
}
