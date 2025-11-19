package dev.namn.steady_fetch

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

data class DownloadChunkWithProgress(
    val chunk: DownloadChunk,
    val progress: Float,
    val downloadedBytes: Long
)

data class DownloadChunk(
    val downloadId:Long,
    val name: String,
    val chunkIndex: Int,
    val totalBytes: Long?,
    val startRange: Long?,
    val endRange: Long?
)

data class DownloadRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val maxParallelChunks: Int = 4,
    val preferredChunkSizeBytes: Long? = null,
    val outputDir: File,
    val fileName: String,
    var chunks: List<DownloadChunk>?
)

data class DownloadQueryResponse(
    val status: DownloadStatus,
    val error: DownloadError?,
    val chunks: List<DownloadChunkWithProgress>,
    val progress: Float
)
