package dev.namn.steady_fetch.impl.managers

import android.util.Log
import dev.namn.steady_fetch.impl.datamodels.DownloadChunk
import dev.namn.steady_fetch.impl.utils.Constants
import kotlin.math.ceil

/**
 * Splits a source file into deterministic chunk definitions so we can resume and parallelise work.
 */
internal class ChunkManager {

    fun generateChunks(fileName: String, fileSize: Long): List<DownloadChunk> {
        require(fileName.isNotBlank()) { "fileName must not be blank" }
        require(fileSize > 0) { "fileSize must be > 0" }

        val chunkSize = determineChunkSize(fileSize)
        val numChunks = ceil(fileSize / chunkSize.toDouble()).toLong()
        Log.d(
            Constants.TAG,
            "Generating $numChunks chunks for $fileName chunkSize=${chunkSize / 1024}KB"
        )

        return (0 until numChunks).map { index ->
            val start = index * chunkSize
            val end = minOf((index + 1) * chunkSize - 1, fileSize - 1)
            DownloadChunk(
                name = chunkFileName(fileName, index + 1, numChunks),
                start = start,
                end = end
            )
        }
    }

    private fun determineChunkSize(fileSize: Long): Long {
        return when {
            fileSize > 1L * 1024 * 1024 * 1024 -> 8L * 1024 * 1024  // 8MB
            fileSize > 100L * 1024 * 1024 -> 4L * 1024 * 1024       // 4MB
            else -> 1L * 1024 * 1024                                // 1MB
        }
    }

    private fun chunkFileName(
        fileName: String,
        chunkNumber: Long,
        totalChunks: Long
    ): String {
        val dot = fileName.lastIndexOf('.')
        val (base, ext) = if (dot >= 0 && dot < fileName.length - 1) {
            fileName.substring(0, dot) to fileName.substring(dot)
        } else {
            fileName to ""
        }

        val width = totalChunks.toString().length
        return "%s%s.part%0${width}d".format(base, ext, chunkNumber)
    }
}
