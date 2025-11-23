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
import java.io.File
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChunkProgressUi(
    val name: String,
    val index: Int,
    val progress: Float,
    val status: DownloadStatus
)

data class DownloadDisplay(
    val id: Long,
    val fileName: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val overallProgress: Float = 0f,
    val chunkProgress: List<ChunkProgressUi> = emptyList()
)

data class DownloadUiState(
    val url: String = "",
    val isDownloading: Boolean = false,
    val downloadId: Long? = null,
    val status: DownloadStatus? = null,
    val errorMessage: String? = null,
    val overallProgress: Float = 0f,
    val chunkProgress: List<ChunkProgressUi> = emptyList(),
    val downloads: Map<Long, DownloadDisplay> = emptyMap(),
    val selectedDownloadId: Long? = null
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState
    private val maxTrackedDownloads = 25

    fun updateUrl(url: String) {
        _uiState.update { it.copy(url = url, errorMessage = null) }
    }

    fun queueDownloads(selectedUrls: List<String>, manualUrl: String) {
        val unique = LinkedHashSet<String>()
        selectedUrls.map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach(unique::add)
        val typed = manualUrl.trim()
        if (typed.isNotBlank()) {
            unique.add(typed)
        }

        if (unique.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please select at least one URL") }
            return
        }

        updateUi { it.copy(errorMessage = null, url = "") }

        viewModelScope.launch(Dispatchers.Default) {
            unique.forEachIndexed { index, downloadUrl ->
                queueDownloadInternal(downloadUrl, isFirst = index == 0)
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

    private suspend fun queueDownloadInternal(url: String, isFirst: Boolean) {
        val outputDir = resolveOutputDirectory()
        val fileName = deriveFileName(url)
        val request = DownloadRequest(
            url = url,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
            ),
            maxParallelDownloads = 8,
            downloadDir = outputDir,
            fileName = fileName
        )

        if (isFirst) {
            updateUi {
                it.copy(
                    isDownloading = true,
                    status = DownloadStatus.QUEUED,
                    overallProgress = 0f,
                    chunkProgress = emptyList()
                )
            }
        }

        val callback = DownloadCallback(fileName)

        val result = runCatching {
            SteadyFetch.queueDownload(request, callback)
        }

        result.onSuccess { downloadId ->
            callback.bind(downloadId)
        }.onFailure { error ->
            updateUi {
                it.copy(
                    errorMessage = error.message ?: "Failed to queue download"
                )
            }
        }
    }

    fun selectDownload(downloadId: Long) {
        updateUi { state ->
            if (!state.downloads.containsKey(downloadId)) {
                state
            } else {
                state.applySelection(downloadId, forceSelection = true)
            }
        }
    }

    private fun registerDownload(downloadId: Long, fileName: String) {
        updateUi { state ->
            val updated = (state.downloads + (downloadId to DownloadDisplay(downloadId, fileName))).trimmed()
            state.copy(downloads = updated).applySelection(downloadId)
        }
    }

    private fun updateDownloadProgress(downloadId: Long, progress: DownloadProgress) {
        val chunkUi = progress.chunkProgress.mapIndexed { index, chunk ->
            ChunkProgressUi(
                name = chunk.name,
                index = index,
                progress = chunk.progress.coerceIn(0f, 1f),
                status = chunk.status
            )
        }
        updateDownloadEntry(downloadId, transform = {
            it.copy(
                status = progress.status,
                overallProgress = progress.progress.coerceIn(0f, 1f),
                chunkProgress = chunkUi
            )
        })
    }

    private fun markDownloadStatus(
        downloadId: Long,
        status: DownloadStatus,
        errorMessage: String? = null
    ) {
        updateDownloadEntry(
            downloadId,
            transform = {
                it.copy(
                    status = status,
                    overallProgress = if (status == DownloadStatus.SUCCESS) 1f else it.overallProgress,
                    chunkProgress = if (status.isTerminal()) emptyList() else it.chunkProgress
                )
            },
            errorMessage = errorMessage
        )
    }

    private fun updateDownloadEntry(
        downloadId: Long,
        transform: (DownloadDisplay) -> DownloadDisplay,
        errorMessage: String? = null
    ) {
        updateUi { state ->
            val display = state.downloads[downloadId]
            if (display == null) {
                state
            } else {
            val newDownloads = (state.downloads + (downloadId to transform(display))).trimmed()
            state.copy(
                downloads = newDownloads,
                errorMessage = errorMessage ?: state.errorMessage
            ).applySelection(state.selectedDownloadId ?: downloadId)
            }
        }
    }

    private fun DownloadUiState.applySelection(
        preferredId: Long? = null,
        forceSelection: Boolean = false
    ): DownloadUiState {
        val preferred = preferredId?.takeIf { downloads.containsKey(it) }
        val current = selectedDownloadId?.takeIf { downloads.containsKey(it) }
        val currentActive = current?.let { downloads[it]?.status.isActive() } == true
        val nextActive = downloads.entries.firstOrNull { it.value.status.isActive() }?.key

        val resolvedId = when {
            forceSelection && preferred != null -> preferred
            preferred != null && downloads[preferred]?.status.isActive() == true -> preferred
            currentActive -> current
            nextActive != null -> nextActive
            preferred != null -> preferred
            current != null -> current
            else -> downloads.keys.firstOrNull()
        }

        val display = resolvedId?.let { downloads[it] }
        val active = downloads.values.any { it.status.isActive() }

        return copy(
            selectedDownloadId = resolvedId,
            downloadId = display?.id,
            status = display?.status,
            overallProgress = display?.overallProgress ?: 0f,
            chunkProgress = display?.chunkProgress ?: emptyList(),
            isDownloading = active
        )
    }

    private inner class DownloadCallback(
        private val fileName: String
    ) : SteadyFetchCallback {
        private var boundId: Long? = null

        fun bind(downloadId: Long) {
            boundId = downloadId
            registerDownload(downloadId, fileName)
        }

        override fun onSuccess() {
            boundId?.let { markDownloadStatus(it, DownloadStatus.SUCCESS) }
        }

        override fun onUpdate(progress: DownloadProgress) {
            boundId?.let { updateDownloadProgress(it, progress) }
        }

        override fun onError(error: DownloadError) {
            val message = error.message.ifEmpty { "Download failed" }
            boundId?.let {
                markDownloadStatus(it, DownloadStatus.FAILED, message)
            } ?: run {
                updateUi { state -> state.copy(errorMessage = message) }
            }
        }
    }

    private fun DownloadStatus?.isActive(): Boolean =
        this == DownloadStatus.RUNNING || this == DownloadStatus.QUEUED

    private fun DownloadStatus?.isTerminal(): Boolean =
        this == DownloadStatus.SUCCESS || this == DownloadStatus.FAILED

    private fun Map<Long, DownloadDisplay>.trimmed(): Map<Long, DownloadDisplay> {
        if (size <= maxTrackedDownloads) return this
        val result = LinkedHashMap<Long, DownloadDisplay>()
        entries.filter { it.value.status.isActive() }.forEach { result[it.key] = it.value }
        val remaining = maxTrackedDownloads - result.size
        if (remaining <= 0) return result
        entries.filter { !it.value.status.isActive() }
            .sortedByDescending { it.value.id }
            .take(remaining)
            .forEach { result[it.key] = it.value.copy(chunkProgress = emptyList()) }
        return result
    }

    private fun updateUi(block: (DownloadUiState) -> DownloadUiState) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _uiState.update(block)
        }
    }
}
