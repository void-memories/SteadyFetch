package dev.namn.steady_fetch.managers

import android.util.Log
import dev.namn.steady_fetch.Constants
import dev.namn.steady_fetch.datamodels.DownloadChunk
import dev.namn.steady_fetch.datamodels.DownloadChunkWithProgress
import dev.namn.steady_fetch.datamodels.DownloadRequest

internal object ChunkManager {

    fun calculateRanges(totalBytes: Long?, preferredChunkSizeBytes: Long?): List<LongRange>? {
        val bytes: Long = totalBytes ?: run {
            Log.i(Constants.TAG_CHUNK_MANAGER, "Chunk calculation skipped: content length unknown")
            return null
        }

        require(bytes > 0) { "totalBytes must be > 0" }

        val preferredSize: Long? = preferredChunkSizeBytes
        val desiredChunkSize =
            if (preferredSize != null && preferredSize >= 1L) preferredSize else Constants.DEFAULT_CHUNK_SIZE_BYTES
        val chunkSizeLong = desiredChunkSize

        val calculatedChunkCountRaw = bytes / chunkSizeLong
        val hasRemainder = bytes % chunkSizeLong != 0L
        val calculatedChunkCount = (calculatedChunkCountRaw + if (hasRemainder) 1L else 0L)
            .coerceAtLeast(1L)
        val chunkCount = calculatedChunkCount.toInt()

        val ranges = mutableListOf<LongRange>()
        var start = 0L
        for (i in 0 until chunkCount) {
            val endExclusive = (start + chunkSizeLong).coerceAtMost(bytes)
            val endInclusive = endExclusive - 1
            ranges += start..endInclusive
            start = endExclusive
        }

        return ranges
    }

    fun createMetadata(
        downloadId: Long,
        request: DownloadRequest,
        totalBytes: Long?,
        chunkRanges: List<LongRange>?
    ): List<DownloadChunk> {
        if (chunkRanges.isNullOrEmpty()) {
            val chunkName = generateChunkName(request.fileName, 1).first()
            return listOf(
                DownloadChunk(
                    downloadId = downloadId,
                    name = chunkName,
                    chunkIndex = 0,
                    totalBytes = totalBytes,
                    startRange = null,
                    endRange = null
                )
            )
        }

        val partNames = generateChunkName(request.fileName, chunkRanges.size)
        return partNames.mapIndexed { index, name ->
            val range = chunkRanges[index]
            val chunkSize = (range.last - range.first + 1).coerceAtLeast(0L)
            DownloadChunk(
                downloadId = downloadId,
                name = name,
                chunkIndex = index,
                totalBytes = chunkSize,
                startRange = range.first,
                endRange = range.last
            )
        }
    }

    fun expectedBytesForChunk(chunk: DownloadChunk): Long? = when {
        chunk.totalBytes != null && chunk.totalBytes > 0 -> chunk.totalBytes
        chunk.startRange != null && chunk.endRange != null -> chunk.endRange - chunk.startRange + 1
        else -> null
    }

    fun expectedBytesForProgress(progress: DownloadChunkWithProgress): Long? =
        expectedBytesForChunk(progress.chunk)

    fun overallProgress(chunks: List<DownloadChunkWithProgress>): Float {
        var expectedBytesSum = 0L
        var downloadedBytesSum = 0L
        var fallbackProgressSum = 0f
        var fallbackCount = 0

        chunks.forEach { chunkProgress ->
            val expectedBytes = expectedBytesForProgress(chunkProgress)
            if (expectedBytes != null && expectedBytes > 0) {
                expectedBytesSum += expectedBytes
                downloadedBytesSum += chunkProgress.downloadedBytes.coerceAtMost(expectedBytes)
            } else if (chunkProgress.progress >= 0f) {
                fallbackProgressSum += chunkProgress.progress
                fallbackCount++
            }
        }

        val computedProgress = when {
            expectedBytesSum > 0 -> (downloadedBytesSum.toDouble() / expectedBytesSum).toFloat()
            fallbackCount > 0 -> fallbackProgressSum / fallbackCount
            else -> 0f
        }

        return computedProgress.coerceIn(0f, 1f)
    }

    fun isChunkComplete(progress: DownloadChunkWithProgress): Boolean {
        val expected = expectedBytesForProgress(progress)
        return when {
            expected != null && expected > 0 -> progress.downloadedBytes >= expected
            progress.progress >= 1f -> true
            else -> false
        }
    }

    private fun generateChunkName(name: String, numChunks: Int): List<String> {
        require(numChunks > 0) { "numChunks must be > 0" }
        require(name.isNotBlank()) { "name must not be blank" }

        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0 && dot < name.length - 1) {
            name.substring(0, dot) to name.substring(dot)
        } else {
            name to ""
        }

        val width = numChunks.toString().length
        return (1..numChunks).map { idx ->
            "%s.part%0${width}d-of-%0${width}d%s".format(base, idx, numChunks, ext)
        }
    }
}
