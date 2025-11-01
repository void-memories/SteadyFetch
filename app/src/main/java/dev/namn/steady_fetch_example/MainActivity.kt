package dev.namn.steady_fetch_example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.namn.steady_fetch.models.DownloadStatus
import dev.namn.steady_fetch_example.ui.theme.SteadyFetchExampleTheme
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SteadyFetchExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DownloadScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "SteadyFetch Downloader",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = uiState.url,
                onValueChange = viewModel::updateUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Download URL") },
                singleLine = true
            )

            Button(
                onClick = viewModel::queueDownload,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isDownloading
            ) {
                Text(if (uiState.isDownloading) "Download in progress" else "Start download")
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        DownloadStatusCard(uiState = uiState)

        if (uiState.chunkProgress.isNotEmpty()) {
            Text(
                text = "Chunk progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.chunkProgress) { chunk ->
                    ChunkProgressCard(chunk)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DownloadScreenPreview() {
    SteadyFetchExampleTheme {
        DownloadStatusCard(
            uiState = DownloadUiState(
                url = "https://example.com/asset.zip",
                isDownloading = true,
                status = DownloadStatus.RUNNING,
                overallProgress = 0.58f,
                totalBytes = 123_456_789,
                chunkProgress = listOf(
                    ChunkProgressUi("chunk-1", 0, 5_000_000, 10_000_000, 0.5f),
                    ChunkProgressUi("chunk-2", 1, 9_000_000, 10_000_000, 0.9f)
                )
            )
        )
    }
}

@Composable
private fun DownloadStatusCard(uiState: DownloadUiState) {
    val statusText = uiState.status?.name ?: "IDLE"
    val formattedBytes = uiState.totalBytes?.let { formatBytesForUi(it) } ?: "Unknown"

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "Total size: $formattedBytes",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = { uiState.overallProgress.coerceIn(0f, 1f) }
            )
        }
    }
}

@Composable
private fun ChunkProgressCard(chunk: ChunkProgressUi) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Chunk ${chunk.index + 1}: ${chunk.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            val downloaded = formatBytesForUi(chunk.downloadedBytes)
            val expected = chunk.expectedBytes?.let { formatBytesForUi(it) } ?: "Unknown"
            Text(
                text = "Downloaded: $downloaded / $expected",
                style = MaterialTheme.typography.bodySmall
            )

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = { chunk.progress.coerceIn(0f, 1f) }
            )
        }
    }
}

private fun formatBytesForUi(bytes: Long): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> "${String.format(Locale.getDefault(), "%.2f", gb)} GB"
        mb >= 1 -> "${String.format(Locale.getDefault(), "%.2f", mb)} MB"
        kb >= 1 -> "${String.format(Locale.getDefault(), "%.2f", kb)} KB"
        else -> "${numberFormat.format(bytes)} B"
    }
}
