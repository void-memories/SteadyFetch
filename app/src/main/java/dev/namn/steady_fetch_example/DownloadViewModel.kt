package dev.namn.steady_fetch_example

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.namn.steady_fetch.models.DownloadChunkWithProgress
import dev.namn.steady_fetch.models.DownloadRequest
import dev.namn.steady_fetch.models.DownloadStatus
import dev.namn.steady_fetch.models.SteadyFetch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ChunkProgressUi(
    val name: String,
    val index: Int,
    val downloadedBytes: Long,
    val expectedBytes: Long?,
    val progress: Float
)

data class DownloadUiState(
    val url: String = "",
    val isDownloading: Boolean = false,
    val downloadId: Long? = null,
    val status: DownloadStatus? = null,
    val errorMessage: String? = null,
    val totalBytes: Long? = null,
    val overallProgress: Float = 0f,
    val chunkProgress: List<ChunkProgressUi> = emptyList()
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState

    private var monitorJob: Job? = null

    fun updateUrl(url: String) {
        _uiState.update { it.copy(url = url, errorMessage = null) }
    }

    fun queueDownload() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid URL") }
            return
        }

        val outputDir = resolveOutputDirectory()
        val fileName = deriveFileName(url)
        val request = DownloadRequest(
            url = url,
            headers = emptyMap(),
            maxParallelChunks = 25,
            outputDir = outputDir,
            fileName = fileName,
            chunks = null
        )

        monitorJob?.cancel()

        viewModelScope.launch {
            val downloadId = SteadyFetch.queue(request)
            if (downloadId == null) {
                _uiState.update { it.copy(errorMessage = "Failed to queue download", isDownloading = false) }
                return@launch
            }

            _uiState.update {
                it.copy(
                    downloadId = downloadId,
                    isDownloading = true,
                    errorMessage = null,
                    status = DownloadStatus.QUEUED,
                    totalBytes = null,
                    overallProgress = 0f,
                    chunkProgress = request.chunks?.mapIndexed { index, chunk ->
                        ChunkProgressUi(
                            name = chunk.name,
                            index = index,
                            downloadedBytes = 0L,
                            expectedBytes = chunk.totalBytes,
                            progress = 0f
                        )
                    } ?: emptyList()
                )
            }

            monitorJob = viewModelScope.launch {
                while (true) {
                    val response = SteadyFetch.query(downloadId)
                    val chunkProgressUi = response.chunks.mapIndexed { index, chunkProgress ->
                        ChunkProgressUi(
                            name = chunkProgress.chunk.name,
                            index = index,
                            downloadedBytes = chunkProgress.downloadedBytes,
                            expectedBytes = calculateExpectedBytes(chunkProgress),
                            progress = chunkProgress.progress.coerceIn(0f, 1f)
                        )
                    }

                    val totalBytes = chunkProgressUi.firstNotNullOfOrNull { it.expectedBytes }
                    val overallProgress = calculateOverallProgress(response.chunks)

                    _uiState.update {
                        it.copy(
                            status = response.status,
                            totalBytes = totalBytes,
                            overallProgress = overallProgress,
                            chunkProgress = chunkProgressUi,
                            isDownloading = response.status == DownloadStatus.RUNNING || response.status == DownloadStatus.QUEUED
                        )
                    }

                    if (response.status == DownloadStatus.SUCCESS || response.status == DownloadStatus.FAILED) {
                        break
                    }

                    delay(1_000)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        monitorJob?.cancel()
    }

    private fun resolveOutputDirectory(): File {
        val appContext = getApplication<Application>()
        val preferred = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return preferred ?: appContext.filesDir
    }

    private fun deriveFileName(url: String): String {
        return Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() } ?: "download.bin"
    }

    private fun calculateExpectedBytes(progress: DownloadChunkWithProgress): Long? {
        val chunk = progress.chunk
        val chunkTotal = chunk.totalBytes
        if (chunkTotal != null && chunkTotal > 0) return chunkTotal

        val start = chunk.startRange
        val end = chunk.endRange
        if (start != null && end != null && end >= start) {
            return end - start + 1
        }

        return if (progress.downloadedBytes > 0) progress.downloadedBytes else null
    }

    private fun calculateOverallProgress(chunks: List<DownloadChunkWithProgress>): Float {
        var expectedBytesSum = 0L
        var downloadedBytesSum = 0L
        var fallbackProgressSum = 0f
        var fallbackCount = 0

        chunks.forEach { chunkProgress ->
            val expectedBytes = calculateExpectedBytes(chunkProgress)
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
}

