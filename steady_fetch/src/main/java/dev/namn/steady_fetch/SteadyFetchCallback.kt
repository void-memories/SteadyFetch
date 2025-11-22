package dev.namn.steady_fetch

import dev.namn.steady_fetch.datamodels.DownloadError
import dev.namn.steady_fetch.datamodels.DownloadProgress

interface SteadyFetchCallback {
    fun onSuccess()
    fun onUpdate(progress: DownloadProgress)
    fun onError(error: DownloadError)
}
