package dev.namn.steady_fetch.impl.extensions

import dev.namn.steady_fetch.impl.uilts.Constants
import dev.namn.steady_fetch.impl.datamodels.DownloadError
import kotlinx.coroutines.CancellationException

internal fun Throwable.convertToDownloadError(): DownloadError {
    fun extractHttpStatusCodeFromMessage(message: String?): Int? {
        if (message.isNullOrBlank()) return null
        val match = Constants.HTTP_CODE_REGEX.find(message)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    if (this is CancellationException) {
        return DownloadError(Constants.ERROR_CODE_CANCELLED, "Download cancelled")
    }

    val message = this.message?.takeUnless { it.isBlank() }
        ?: this::class.java.simpleName
    val httpCode = extractHttpStatusCodeFromMessage(message)

    val code = when {
        httpCode != null -> httpCode
        this is IllegalArgumentException || this is IllegalStateException -> Constants.ERROR_CODE_BAD_REQUEST
        else -> Constants.ERROR_CODE_INTERNAL_SERVER_ERROR
    }

    return DownloadError(code, message)
}
