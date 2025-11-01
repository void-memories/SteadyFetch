package dev.namn.steady_fetch.models

internal const val DEFAULT_CHUNK_SIZE_BYTES: Long = 5L * 1024 * 1024
internal const val DEFAULT_MAX_SUPPORTED_CHUNKS: Int = 16

internal fun formatByteCount(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$bytes bytes"
    }
}

internal fun generateChunkNames(name: String, numChunks: Int): List<String> {
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

internal fun expectedBytesForChunk(chunk: DownloadChunk): Long? = when {
    chunk.totalBytes != null && chunk.totalBytes > 0 -> chunk.totalBytes
    chunk.startRange != null && chunk.endRange != null -> chunk.endRange - chunk.startRange + 1
    else -> null
}

internal fun expectedBytesForProgress(progress: DownloadChunkWithProgress): Long? =
    expectedBytesForChunk(progress.chunk)

internal fun calculateOverallProgress(chunks: List<DownloadChunkWithProgress>): Float {
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

internal fun isChunkComplete(progress: DownloadChunkWithProgress): Boolean {
    val expected = expectedBytesForProgress(progress)
    return when {
        expected != null && expected > 0 -> progress.downloadedBytes >= expected
        progress.progress >= 1f -> true
        else -> false
    }
}

