package dev.namn.steady_fetch.datamodels

import java.io.File

data class DownloadRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val maxParallelDownloads: Int = 4,
    val downloadDir: File,
    val fileName: String,
)

data class DownloadMetadata(
    val request: DownloadRequest,
    val chunks: List<DownloadChunk>?
)

data class DownloadChunk(
    val name: String,
    val start: Long,
    val end: Long,
)
