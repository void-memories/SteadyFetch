package dev.namn.steady_fetch.impl.callbacks

import dev.namn.steady_fetch.impl.datamodels.DownloadError
import dev.namn.steady_fetch.impl.datamodels.DownloadProgress
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SteadyFetchCallbackTest {

    @Test
    fun callback_contract_invokesAllMethods() {
        val events = mutableListOf<String>()
        val callback = object : SteadyFetchCallback {
            override fun onSuccess() {
                events += "success"
            }

            override fun onUpdate(progress: DownloadProgress) {
                events += "update:${progress.status}"
            }

            override fun onError(error: DownloadError) {
                events += "error:${error.code}"
            }
        }

        callback.onUpdate(
            DownloadProgress(
                status = DownloadStatus.RUNNING,
                progress = 0.5f,
                chunkProgress = emptyList()
            )
        )
        callback.onError(DownloadError(500, "boom"))
        callback.onSuccess()

        assertEquals(listOf("update:RUNNING", "error:500", "success"), events)
    }
}

