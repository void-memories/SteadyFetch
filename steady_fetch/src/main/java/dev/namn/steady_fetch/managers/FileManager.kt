package dev.namn.steady_fetch.managers

import android.os.StatFs
import android.util.Log
import dev.namn.steady_fetch.Constants
import dev.namn.steady_fetch.datamodels.DownloadChunk
import dev.namn.steady_fetch.progress.DownloadProgressStore
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class FileManager(
    private val progressStore: DownloadProgressStore
) {
    fun ensureDownloadDirectoryExists(directory: File) {
        val alreadyExists = directory.exists()
        if (!alreadyExists && !directory.mkdirs()) {
            val message = "Unable to create download directory: ${directory.absolutePath}"
            Log.e(Constants.TAG_FILE_MANAGER, message)
            throw IllegalStateException(message)
        }

        if (!directory.exists()) {
            val message = "Download directory unavailable after creation attempt: ${directory.absolutePath}"
            Log.e(Constants.TAG_FILE_MANAGER, message)
            throw IllegalStateException(message)
        }

        if (!directory.isDirectory) {
            val message = "Download path is not a directory: ${directory.absolutePath}"
            Log.e(Constants.TAG_FILE_MANAGER, message)
            throw IllegalStateException(message)
        }

        if (!alreadyExists) {
            Log.d(Constants.TAG_FILE_MANAGER, "Created download directory at ${directory.absolutePath}")
        }
    }

    fun writeChunkToFile(
        body: ResponseBody,
        file: File,
        chunk: DownloadChunk,
        expectedBytes: Long?,
        downloadId: Long
    ) {
        createDirectoryIfNotExists(file.parentFile)
        FileOutputStream(file, false).use { outputStream ->
            body.byteStream().use { inputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloadedBytes = 0L

                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    outputStream.write(buffer, 0, read)
                    downloadedBytes += read
                    progressStore.updateChunkProgress(downloadId, chunk, downloadedBytes, expectedBytes)
                }

                outputStream.flush()
                progressStore.updateChunkProgress(downloadId, chunk, expectedBytes ?: downloadedBytes, expectedBytes)
            }
        }
    }

    fun validateStorageCapacity(destinationDir: File, expectedBytes: Long?) {
        if (expectedBytes == null) {
            Log.i(Constants.TAG_FILE_MANAGER, "Storage check skipped: content length unknown")
            return
        }

        val availableBytes = StatFs(destinationDir.absolutePath).availableBytes
        val requiredBytes = (expectedBytes * Constants.STORAGE_SAFETY_MARGIN_PERCENT).toLong()

        if (availableBytes < requiredBytes) {
            val message = "Insufficient storage space. " +
                "Required: ${formatBytesToHumanReadable(requiredBytes)}, " +
                "Available: ${formatBytesToHumanReadable(availableBytes)}"
            Log.e(Constants.TAG_FILE_MANAGER, message)
            throw IllegalStateException(message)
        }

        Log.d(
            Constants.TAG_FILE_MANAGER,
            "Storage check passed. Available: ${formatBytesToHumanReadable(availableBytes)}, " +
                "Required: ${formatBytesToHumanReadable(requiredBytes)}"
        )
    }

    private fun createDirectoryIfNotExists(dir: File?) {
        if (dir != null && !dir.exists()) {
            if (!dir.mkdirs() && !dir.exists()) {
                val message = "Unable to create directory: ${dir.absolutePath}"
                Log.e(Constants.TAG_FILE_MANAGER, message)
                throw IOException(message)
            }
        }
    }

    private fun formatBytesToHumanReadable(bytes: Long): String {
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

}
