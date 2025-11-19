package dev.namn.steady_fetch.datamodels

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

