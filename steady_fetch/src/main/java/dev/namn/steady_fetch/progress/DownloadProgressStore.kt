package dev.namn.steady_fetch.progress

import dev.namn.steady_fetch.datamodels.DownloadMetadata
import java.util.concurrent.ConcurrentHashMap

internal class DownloadStore {
    private val activeDownloads =
        ConcurrentHashMap<Long, DownloadMetadata>()

    fun add(downloadId: Long, metadata: DownloadMetadata) {
        activeDownloads[downloadId] = metadata
    }
}
