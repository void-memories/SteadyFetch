package dev.namn.steady_fetch_example

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.namn.steady_fetch.SteadyFetch
import dev.namn.steady_fetch.impl.callbacks.SteadyFetchCallback
import dev.namn.steady_fetch.impl.datamodels.DownloadError
import dev.namn.steady_fetch.impl.datamodels.DownloadProgress
import dev.namn.steady_fetch.impl.datamodels.DownloadRequest
import dev.namn.steady_fetch.impl.datamodels.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ChunkProgressUi(
    val name: String,
    val index: Int,
    val progress: Float,
    val status: DownloadStatus
)

data class DownloadUiState(
    val url: String = "",
    val isDownloading: Boolean = false,
    val downloadId: Long? = null,
    val status: DownloadStatus? = null,
    val errorMessage: String? = null,
    val overallProgress: Float = 0f,
    val chunkProgress: List<ChunkProgressUi> = emptyList()
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState

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
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
            ),
            maxParallelDownloads = 4,
            downloadDir = outputDir,
            fileName = fileName
        )

        val callback = createCallback()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    errorMessage = null,
                    status = DownloadStatus.QUEUED,
                    overallProgress = 0f,
                    chunkProgress = emptyList()
                )
            }

            try {
                val downloadId = SteadyFetch.queueDownload(request, callback)
                _uiState.update { it.copy(downloadId = downloadId) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.message ?: "Failed to queue download",
                        isDownloading = false,
                        status = DownloadStatus.FAILED
                    )
                }
            }
        }
    }

    private fun resolveOutputDirectory(): File {
        val appContext = getApplication<Application>()
        val preferred = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return preferred ?: appContext.filesDir
    }

    private fun deriveFileName(url: String): String {
        return Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() } ?: "download.bin"
    }

    private fun createCallback(): SteadyFetchCallback {
        return object : SteadyFetchCallback {
            override fun onSuccess() {
                postStateUpdate {
                    it.copy(
                        status = DownloadStatus.SUCCESS,
                        overallProgress = 1f,
                        isDownloading = false
                    )
                }
            }

            override fun onUpdate(progress: DownloadProgress) {
                val chunkProgressUi = progress.chunkProgress.mapIndexed { index, chunk ->
                    ChunkProgressUi(
                        name = chunk.name,
                        index = index,
                        progress = chunk.progress.coerceIn(0f, 1f),
                        status = chunk.status
                    )
                }

                postStateUpdate {
                    it.copy(
                        status = progress.status,
                        overallProgress = progress.progress.coerceIn(0f, 1f),
                        chunkProgress = chunkProgressUi,
                        isDownloading = progress.status == DownloadStatus.RUNNING || progress.status == DownloadStatus.QUEUED,
                        errorMessage = null
                    )
                }
            }

            override fun onError(error: DownloadError) {
                postStateUpdate {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        isDownloading = false,
                        errorMessage = error.message.ifEmpty { "Download failed" }
                    )
                }
            }
        }
    }

    private fun postStateUpdate(block: (DownloadUiState) -> DownloadUiState) {
        viewModelScope.launch {
            _uiState.update(block)
        }
    }
}
