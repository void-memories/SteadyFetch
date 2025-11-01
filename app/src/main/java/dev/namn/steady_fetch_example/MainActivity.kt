package dev.namn.steady_fetch_example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.namn.steady_fetch.models.DownloadStatus
import dev.namn.steady_fetch_example.ui.theme.SteadyFetchExampleTheme
import java.text.NumberFormat
import java.util.Locale

private val StartColor = Color(0xFFFF3864)
private val MidColor = Color(0xFFFFC14E)
private val EndColor = Color(0xFF37FF8B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SteadyFetchExampleTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
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
}

@Composable
fun DownloadScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    DownloadScreenBody(
        uiState = uiState,
        onUrlChange = viewModel::updateUrl,
        onChunkSizeChange = viewModel::updateChunkSizeMb,
        onDownloadClick = viewModel::queueDownload,
        chunkSizeRange = viewModel.chunkSizeRange(),
        modifier = modifier
    )
}

@Composable
private fun DownloadScreenBody(
    uiState: DownloadUiState,
    onUrlChange: (String) -> Unit,
    onChunkSizeChange: (Float) -> Unit,
    onDownloadClick: () -> Unit,
    chunkSizeRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderSection(uiState.status)
        StatusSection(uiState)
        ChunkGrid(
            chunks = uiState.chunkProgress,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        ChunkSizeSelector(
            currentValueMb = uiState.preferredChunkSizeMb,
            onValueChange = onChunkSizeChange,
            range = chunkSizeRange
        )
        UrlInputSection(
            url = uiState.url,
            isDownloading = uiState.isDownloading,
            onUrlChange = onUrlChange,
            onDownloadClick = onDownloadClick,
            errorMessage = uiState.errorMessage
        )
    }
}

@Composable
private fun HeaderSection(status: DownloadStatus?) {
    val resolvedStatus = status ?: DownloadStatus.QUEUED
    val badgeColor = when (resolvedStatus) {
        DownloadStatus.SUCCESS -> EndColor
        DownloadStatus.RUNNING -> MidColor
        DownloadStatus.FAILED -> StartColor
        DownloadStatus.QUEUED -> Color(0xFF9AA0FF)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "SteadyFetch",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .background(badgeColor.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = resolvedStatus.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = badgeColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusSection(uiState: DownloadUiState) {
    val progress = uiState.overallProgress.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            progress = { progress },
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            color = lerp(StartColor, EndColor, progress)
        )
        uiState.totalBytes?.let {
            Text(
                text = "${formatBytesForUi(it)} total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ChunkGrid(chunks: List<ChunkProgressUi>, modifier: Modifier = Modifier) {
    if (chunks.isEmpty()) {
        EmptyState(modifier)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 72.dp),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(chunks) { chunk ->
            ChunkTile(chunk)
        }
    }
}

@Composable
private fun ChunkTile(chunk: ChunkProgressUi) {
    val progress = chunk.progress.coerceIn(0f, 1f)
    val tileColor = when {
        progress < 0.5f -> lerp(StartColor, MidColor, progress * 2f)
        else -> lerp(MidColor, EndColor, (progress - 0.5f) * 2f)
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .background(tileColor.copy(alpha = 0.42f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Chunk ${chunk.index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "No downloads",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Add a link to begin",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            textAlign = TextAlign.Center
        )
    }
    }
}

@Composable
private fun ChunkSizeSelector(
    currentValueMb: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Chunk size: ${currentValueMb.toInt()} MB",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        Slider(
            value = currentValueMb,
            onValueChange = onValueChange,
            valueRange = range,
            steps = ((range.endInclusive - range.start).toInt() - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun UrlInputSection(
    url: String,
    isDownloading: Boolean,
    onUrlChange: (String) -> Unit,
    onDownloadClick: () -> Unit,
    errorMessage: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Download URL") },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )

        errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = StartColor
            )
        }

        Button(
            onClick = onDownloadClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !isDownloading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                text = if (isDownloading) "Downloading…" else "Add URL",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
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

@Preview(showBackground = true, backgroundColor = 0xFF05070F)
@Composable
private fun DownloadScreenPreview() {
    SteadyFetchExampleTheme {
        val previewState = DownloadUiState(
            url = "https://example.com/file.zip",
            status = DownloadStatus.RUNNING,
            isDownloading = true,
            overallProgress = 0.58f,
            totalBytes = 123_000_000,
            preferredChunkSizeMb = 6f,
            chunkProgress = listOf(
                ChunkProgressUi("chunk-1", 0, 2_000_000, 5_000_000, 0.4f),
                ChunkProgressUi("chunk-2", 1, 4_500_000, 5_000_000, 0.9f),
                ChunkProgressUi("chunk-3", 2, 1_000_000, 5_000_000, 0.2f)
            )
        )
        DownloadScreenBody(
            uiState = previewState,
            onUrlChange = {},
            onChunkSizeChange = {},
            onDownloadClick = {},
            chunkSizeRange = 1f..20f,
            modifier = Modifier.fillMaxSize()
        )
    }
}
