package dev.namn.steady_fetch

import dev.namn.steady_fetch.datamodels.DownloadChunk
import dev.namn.steady_fetch.datamodels.DownloadChunkProgress
import dev.namn.steady_fetch.datamodels.DownloadStatus

class ChunkProgressTracker(chunks: List<DownloadChunk>) {

    private val state = chunks
        .map { chunk ->
            DownloadChunkProgress(
                status = DownloadStatus.QUEUED,
                name = chunk.name,
                progress = 0f
            )
        }
        .toMutableList()

    fun snapshot(): List<DownloadChunkProgress> = synchronized(state) {
        state.map { it.copy() }
    }

    fun markRunning(index: Int): List<DownloadChunkProgress> =
        update(index, status = DownloadStatus.RUNNING, progress = null)

    fun updateProgress(index: Int, progress: Float): List<DownloadChunkProgress> =
        update(index, status = null, progress = progress)

    fun markSuccess(index: Int): List<DownloadChunkProgress> =
        update(index, status = DownloadStatus.SUCCESS, progress = 1f)

    fun markFailed(index: Int): List<DownloadChunkProgress> =
        update(index, status = DownloadStatus.FAILED, progress = 0f)

    private fun update(
        index: Int,
        status: DownloadStatus?,
        progress: Float?
    ): List<DownloadChunkProgress> = synchronized(state) {
        val current = state[index]
        state[index] = current.copy(
            status = status ?: current.status,
            progress = progress ?: current.progress
        )
        state.map { it.copy() }
    }
}
