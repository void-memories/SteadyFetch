package dev.namn.steady_fetch

internal object Constants {
    const val TAG = "STEADY_FETCH"
    const val TAG_STEADY_FETCH_CONTROLLER = "SteadyFetchController"
    const val TAG_NETWORK_DOWNLOADER = "NetworkDownloader"
    const val TAG_CHUNK_MANAGER = "ChunkManager"
    const val TAG_FILE_MANAGER = "FileManager"

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
