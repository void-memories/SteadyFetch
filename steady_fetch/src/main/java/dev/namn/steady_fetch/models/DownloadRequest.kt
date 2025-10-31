package dev.namn.steady_fetch.models

import java.io.File

data class DownloadError(
    val code: Int,
    val message: String
)

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    FAILED,
    SUCCESS
}

data class ChunkProgress(
    val downloadId: Long,
    val chunkIndex: Int,
    val downloadedBytes: Long,
    val totalBytes: Long?
)

data class DownloadRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val maxConnections: Int = 4,
    val outputDir: File
)

data class DownloadQueryResponse(
    val status: DownloadStatus,
    val error: DownloadError?,
    val chunks: List<ChunkProgress>,
    val progress: Float
)
