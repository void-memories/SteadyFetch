import dev.namn.steady_fetch.datamodels.DownloadChunk
import kotlin.math.ceil

class ChunkManager() {
    fun generateChunks(fileName: String, fileSize: Long): List<DownloadChunk> {
        require(fileSize > 0) { "fileSize must be > 0" }

        val chunkSize = getChunkSize(fileSize)
        val numChunks = ceil(fileSize / chunkSize.toDouble()).toLong()

        return (0 until numChunks).map { i ->
            val start = i * chunkSize
            val end = minOf((i + 1) * chunkSize - 1, fileSize - 1)
            DownloadChunk(generateChunkFileName(fileName, i + 1, numChunks), start, end)
        }
    }

    private fun getChunkSize(fileSize: Long): Long {
        return when {
            fileSize > 1L * 1024 * 1024 * 1024 -> 8L * 1024 * 1024  // 8MB
            fileSize > 100L * 1024 * 1024 -> 4L * 1024 * 1024       // 4MB
            else -> 1L * 1024 * 1024                                // 1MB
        }
    }

    private fun generateChunkFileName(
        fileName: String, chunkNumber: Long,
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
