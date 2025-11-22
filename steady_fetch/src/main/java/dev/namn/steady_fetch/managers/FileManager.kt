package dev.namn.steady_fetch.managers

import android.os.StatFs
import android.util.Log
import dev.namn.steady_fetch.Constants
import java.io.File
import java.io.IOException

internal class FileManager(
) {
    fun validateStorageCapacity(destinationDir: File, expectedBytes: Long) {
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

     fun createDirectoryIfNotExists(dir: File) {
        if (!dir.exists()) {
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
