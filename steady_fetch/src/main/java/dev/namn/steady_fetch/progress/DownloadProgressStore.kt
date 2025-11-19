package dev.namn.steady_fetch.progress

import dev.namn.steady_fetch.datamodels.DownloadChunk
import dev.namn.steady_fetch.datamodels.DownloadChunkWithProgress

import java.util.concurrent.ConcurrentHashMap

internal class DownloadProgressStore {
    private val progressStore =
        ConcurrentHashMap<Long, ConcurrentHashMap<String, DownloadChunkWithProgress>>()

    fun initializeDownloadProgress(downloadId: Long, chunks: List<DownloadChunk>) {
        val progressMap = ConcurrentHashMap<String, DownloadChunkWithProgress>()
        chunks.forEach { chunk ->
            progressMap[chunk.name] = DownloadChunkWithProgress(
                chunk = chunk,
                progress = 0f,
                downloadedBytes = 0L
            )
        }
        progressStore[downloadId] = progressMap
    }

    fun updateChunkProgress(downloadId: Long, chunk: DownloadChunk, downloadedBytes: Long, expectedBytes: Long?) {
        val progress = if (expectedBytes != null && expectedBytes > 0) {
            (downloadedBytes.coerceAtMost(expectedBytes).toDouble() / expectedBytes).toFloat()
        } else {
            -1f
        }

        progressStore[downloadId]?.compute(chunk.name) { _, _ ->
            DownloadChunkWithProgress(
                chunk = chunk,
                progress = progress,
                downloadedBytes = downloadedBytes
            )
        }
    }

    fun getProgressSnapshot(downloadId: Long): Map<String, DownloadChunkWithProgress> =
        progressStore[downloadId]?.toMap() ?: emptyMap()

    fun clearDownloadProgress(downloadId: Long) {
        progressStore.remove(downloadId)
    }
}

