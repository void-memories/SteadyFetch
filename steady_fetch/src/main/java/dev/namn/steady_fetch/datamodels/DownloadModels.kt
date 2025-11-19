package dev.namn.steady_fetch.datamodels

import java.io.File

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

internal data class PreparedDownload(
    val request: DownloadRequest,
    val totalBytes: Long?
)

