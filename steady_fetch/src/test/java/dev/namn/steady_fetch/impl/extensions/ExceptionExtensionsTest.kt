package dev.namn.steady_fetch.impl.extensions

import dev.namn.steady_fetch.impl.datamodels.DownloadError
import dev.namn.steady_fetch.impl.utils.Constants
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Test

class ExceptionExtensionsTest {

    @Test
    fun convertToDownloadError_handlesCancellation() {
        val error = CancellationException("cancelled").convertToDownloadError()
        assertEquals(Constants.ERROR_CODE_CANCELLED, error.code)
        assertEquals("Download cancelled", error.message)
    }

    @Test
    fun convertToDownloadError_extractsHttpCode() {
        val throwable = RuntimeException("HTTP 503 upstream error")
        val error = throwable.convertToDownloadError()
        assertEquals(503, error.code)
        assertEquals("HTTP 503 upstream error", error.message)
    }

    @Test
    fun convertToDownloadError_mapsIllegalArgumentToBadRequest() {
        val error = IllegalArgumentException("bad input").convertToDownloadError()
        assertEquals(Constants.ERROR_CODE_BAD_REQUEST, error.code)
    }

    @Test
    fun convertToDownloadError_defaultsToInternalError() {
        val error = IllegalStateException("unknown issue").convertToDownloadError()
        assertEquals(Constants.ERROR_CODE_BAD_REQUEST, error.code)
    }
}

