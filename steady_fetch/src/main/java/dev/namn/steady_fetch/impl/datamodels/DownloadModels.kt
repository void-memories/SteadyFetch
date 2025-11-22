package dev.namn.steady_fetch.impl.datamodels

import dev.namn.steady_fetch.impl.utils.Constants
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

/**
 * Defines a single download and how SteadyFetch should execute it.
 */
data class DownloadRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val maxParallelDownloads: Int = 4,
    val downloadDir: File,
    val fileName: String,
)

data class DownloadMetadata(
    val request: DownloadRequest,
    val chunks: List<DownloadChunk>?,
    val contentMd5: String? = null,
)

data class DownloadChunk(
    val name: String,
    val start: Long,
    val end: Long,
)

data class SteadyFetchError(
    val code: Int,
    val message: String
)

/**
 * Snapshot that is relayed back to client apps through [dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback].
 */
data class DownloadProgress(
    val status: DownloadStatus,
    val progress: Float,
    val chunkProgress: List<DownloadChunkProgress>
)

data class DownloadChunkProgress(
    val status: DownloadStatus,
    val name: String,
    val progress: Float
) {
    fun updateProgress(progress: Float): DownloadChunkProgress {
        return DownloadChunkProgress(this.status, this.name, progress)
    }

    fun updateStatus(status: DownloadStatus): DownloadChunkProgress {
        return DownloadChunkProgress(status, this.name, this.progress)
    }
}
