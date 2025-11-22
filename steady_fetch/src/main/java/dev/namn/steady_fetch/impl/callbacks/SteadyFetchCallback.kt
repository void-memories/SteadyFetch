package dev.namn.steady_fetch.impl.callbacks

import dev.namn.steady_fetch.impl.datamodels.DownloadError
import dev.namn.steady_fetch.impl.datamodels.DownloadProgress

interface SteadyFetchCallback {
    fun onSuccess()
    fun onUpdate(progress: DownloadProgress)
    fun onError(error: DownloadError)
}
