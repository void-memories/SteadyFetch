package dev.namn.steady_fetch.managers

import android.os.StatFs
import android.util.Log
import dev.namn.steady_fetch.Constants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

internal class FileManager {
    fun validateStorageCapacity(destinationDir: File, expectedBytes: Long) {
        val availableBytes = StatFs(destinationDir.absolutePath).availableBytes
        val requiredBytes = (expectedBytes * Constants.STORAGE_SAFETY_MARGIN_PERCENT).toLong()

        if (availableBytes < requiredBytes) {
            val message = "Insufficient storage. required=${formatBytesToHumanReadable(requiredBytes)}, available=${formatBytesToHumanReadable(availableBytes)}"
            Log.e(Constants.TAG, message)
            throw IllegalStateException(message)
        }

        Log.d(
            Constants.TAG,
            "Storage verified. available=${formatBytesToHumanReadable(availableBytes)} required=${formatBytesToHumanReadable(requiredBytes)}"
        )
    }

     fun createDirectoryIfNotExists(dir: File) {
        if (!dir.exists()) {
            if (!dir.mkdirs() && !dir.exists()) {
                val message = "Unable to create directory: ${dir.absolutePath}"
                Log.e(Constants.TAG, message)
                throw IOException(message)
            }
            Log.i(Constants.TAG, "Created directory ${dir.absolutePath}")
        } else {
            Log.d(Constants.TAG, "Directory already exists ${dir.absolutePath}")
        }
    }

    fun reconcileChunks(
        downloadDir: File,
        fileName: String,
        chunks: List<dev.namn.steady_fetch.datamodels.DownloadChunk>
    ) {
        if (chunks.isEmpty()) return

        val orderedChunks = chunks.sortedBy { it.start }
        val finalFile = File(downloadDir, fileName)
        val tempFile = File(downloadDir, "$fileName.__assembling")

        try {
            FileOutputStream(tempFile).use { output ->
                orderedChunks.forEach { chunk ->
                    val chunkFile = File(downloadDir, chunk.name)
                    if (!chunkFile.exists()) {
                        throw IOException("Missing chunk file ${chunkFile.absolutePath}")
                    }
                    FileInputStream(chunkFile).use { input ->
                        input.copyTo(output)
                    }
                }
                output.fd.sync()
            }

            if (finalFile.exists() && !finalFile.delete()) {
                throw IOException("Unable to delete existing file ${finalFile.absolutePath}")
            }

            if (!tempFile.renameTo(finalFile)) {
                throw IOException("Unable to finalize file ${finalFile.absolutePath}")
            }

            orderedChunks.forEach { chunk ->
                val chunkFile = File(downloadDir, chunk.name)
                if (chunkFile.exists() && !chunkFile.delete()) {
                    Log.w(Constants.TAG, "Failed to delete chunk ${chunkFile.absolutePath}")
                }
            }
            Log.i(Constants.TAG, "Reconciled ${orderedChunks.size} chunks into ${finalFile.absolutePath}")
        } catch (e: IOException) {
            tempFile.delete()
            throw e
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
