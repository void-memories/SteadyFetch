package dev.namn.steady_fetch.models

import kotlinx.coroutines.CancellationException

private val HTTP_CODE_REGEX = Regex("HTTP\\s+(\\d{3})")

fun Throwable.toDownloadError(): DownloadError {
    if (this is CancellationException) {
        return DownloadError(499, "Download cancelled")
    }

    val message = this.message?.takeUnless { it.isBlank() }
        ?: this::class.java.simpleName
    val httpCode = extractHttpCode(message)

    val code = when {
        httpCode != null -> httpCode
        this is IllegalArgumentException || this is IllegalStateException -> 400
        else -> 500
    }

    return DownloadError(code, message)
}

private fun extractHttpCode(message: String?): Int? {
    if (message.isNullOrBlank()) return null
    val match = HTTP_CODE_REGEX.find(message)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull()
}
