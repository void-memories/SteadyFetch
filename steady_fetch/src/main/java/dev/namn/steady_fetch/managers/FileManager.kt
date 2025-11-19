package dev.namn.steady_fetch.managers

import android.os.StatFs
import android.util.Log
import dev.namn.steady_fetch.DownloadChunk
import dev.namn.steady_fetch.progress.DownloadProgressStore
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class FileManager(
    private val progressStore: DownloadProgressStore
) {
    fun prepareDirectory(directory: File) {
        val alreadyExists = directory.exists()
        if (!alreadyExists && !directory.mkdirs()) {
            val message = "Unable to create download directory: ${directory.absolutePath}"
            Log.e(TAG, message)
            throw IllegalStateException(message)
        }

        if (!directory.exists()) {
            val message = "Download directory unavailable after creation attempt: ${directory.absolutePath}"
            Log.e(TAG, message)
            throw IllegalStateException(message)
        }

        if (!directory.isDirectory) {
            val message = "Download path is not a directory: ${directory.absolutePath}"
            Log.e(TAG, message)
            throw IllegalStateException(message)
        }

        if (!alreadyExists) {
            Log.d(TAG, "Created download directory at ${directory.absolutePath}")
        }
    }

    fun writeChunk(
        body: ResponseBody,
        file: File,
        chunk: DownloadChunk,
        expectedBytes: Long?,
        downloadId: Long
    ) {
        ensureDirectoryExists(file.parentFile)
        FileOutputStream(file, false).use { outputStream ->
            body.byteStream().use { inputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloadedBytes = 0L

                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    outputStream.write(buffer, 0, read)
                    downloadedBytes += read
                    progressStore.update(downloadId, chunk, downloadedBytes, expectedBytes)
                }

                outputStream.flush()
                progressStore.update(downloadId, chunk, expectedBytes ?: downloadedBytes, expectedBytes)
            }
        }
    }

    fun ensureCapacity(destinationDir: File, expectedBytes: Long?) {
        if (expectedBytes == null) {
            Log.i(TAG, "Storage check skipped: content length unknown")
            return
        }

        val availableBytes = StatFs(destinationDir.absolutePath).availableBytes
        val requiredBytes = (expectedBytes * STORAGE_SAFETY_MARGIN_PERCENT).toLong()

        if (availableBytes < requiredBytes) {
            val message = "Insufficient storage space. " +
                "Required: ${formatBytes(requiredBytes)}, " +
                "Available: ${formatBytes(availableBytes)}"
            Log.e(TAG, message)
            throw IllegalStateException(message)
        }

        Log.d(
            TAG,
            "Storage check passed. Available: ${formatBytes(availableBytes)}, " +
                "Required: ${formatBytes(requiredBytes)}"
        )
    }

    private fun ensureDirectoryExists(dir: File?) {
        if (dir != null && !dir.exists()) {
            if (!dir.mkdirs() && !dir.exists()) {
                val message = "Unable to create directory: ${dir.absolutePath}"
                Log.e(TAG, message)
                throw IOException(message)
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
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

    companion object {
        private const val TAG = "FileManager"
        private const val STORAGE_SAFETY_MARGIN_PERCENT = 1.1
    }
}
