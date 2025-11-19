package dev.namn.steady_fetch.util

import dev.namn.steady_fetch.Constants
import dev.namn.steady_fetch.datamodels.DownloadError
import kotlinx.coroutines.CancellationException

internal fun Throwable.toDownloadError(): DownloadError {
    if (this is CancellationException) {
        return DownloadError(Constants.ERROR_CODE_CANCELLED, "Download cancelled")
    }

    val message = this.message?.takeUnless { it.isBlank() }
        ?: this::class.java.simpleName
    val httpCode = extractHttpCode(message)

    val code = when {
        httpCode != null -> httpCode
        this is IllegalArgumentException || this is IllegalStateException -> Constants.ERROR_CODE_BAD_REQUEST
        else -> Constants.ERROR_CODE_INTERNAL_SERVER_ERROR
    }

    return DownloadError(code, message)
}

private fun extractHttpCode(message: String?): Int? {
    if (message.isNullOrBlank()) return null
    val match = Constants.HTTP_CODE_REGEX.find(message)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull()
}

