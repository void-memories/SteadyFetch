package dev.namn.steady_fetch.impl.uilts

internal object Constants {
    const val TAG = "STEADY_FETCH"
    const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 10L
    const val DEFAULT_READ_TIMEOUT_SECONDS = 10L

    const val DEFAULT_BUFFER_SIZE = 8 * 1024

    const val DEFAULT_CHUNK_SIZE_BYTES: Long = 5L * 1024 * 1024
    const val MAX_PARALLEL_CHUNKS = 25

    const val STORAGE_SAFETY_MARGIN_PERCENT = 1.1

    const val ERROR_CODE_NOT_FOUND = 404
    const val ERROR_CODE_CANCELLED = 499
    const val ERROR_CODE_BAD_REQUEST = 400
    const val ERROR_CODE_INTERNAL_SERVER_ERROR = 500

    val HTTP_CODE_REGEX = Regex("HTTP\\s+(\\d{3})")
}
