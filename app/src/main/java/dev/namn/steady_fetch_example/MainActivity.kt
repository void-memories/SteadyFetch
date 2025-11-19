package dev.namn.steady_fetch_example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.namn.steady_fetch.datamodels.DownloadStatus
import dev.namn.steady_fetch_example.ui.theme.SteadyFetchExampleTheme
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val BackgroundTopColor = Color(0xFF090B13)
private val BackgroundBottomColor = Color(0xFF05060C)
private val GridBaseColor = Color(0xFF161927)
private val GridAccentColor = Color(0xFF586BFF)
private val GridIdleColor = Color.White.copy(alpha = 0.04f)
private val GridBorderColor = Color.White.copy(alpha = 0.12f)
private val HudBackgroundColor = Color(0xFF101322)
private val HudBorderColor = Color.White.copy(alpha = 0.18f)
private val HudGlowColor = Color(0xFF586BFF)
private val HudAccentColor = Color(0xFF7C8AFF)
private val HudInputOverlay = Color.White.copy(alpha = 0.05f)

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
        onDownloadClick = viewModel::queueDownload,
        modifier = modifier
    )
}

@Composable
private fun DownloadScreenBody(
    uiState: DownloadUiState,
    onUrlChange: (String) -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showUrlDialog by remember { mutableStateOf(false) }
    val progress = uiState.overallProgress.coerceIn(0f, 1f)
    val status = uiState.status ?: DownloadStatus.QUEUED

    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BackgroundTopColor, BackgroundBottomColor)
                )
            )
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        ChunkMatrix(
            chunks = uiState.chunkProgress,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 140.dp)
        )

        BottomHud(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            progress = progress,
            status = status,
            totalBytes = uiState.totalBytes,
            isDownloading = uiState.isDownloading,
            onAddClick = { showUrlDialog = true }
        )
    }

    if (showUrlDialog) {
        UrlInputDialog(
            url = uiState.url,
            onUrlChange = onUrlChange,
            onDismiss = { showUrlDialog = false },
            onConfirm = {
                showUrlDialog = false
                onDownloadClick()
            },
            errorMessage = uiState.errorMessage,
            isDownloading = uiState.isDownloading
        )
    }
}

@Composable
private fun ChunkMatrix(chunks: List<ChunkProgressUi>, modifier: Modifier = Modifier) {
    val minimumCells = 20
    val actualItems = chunks.map { it as ChunkProgressUi? }
    val displayItems: List<ChunkProgressUi?> = when {
        actualItems.isEmpty() -> List(minimumCells) { null }
        actualItems.size >= minimumCells -> actualItems
        else -> actualItems + List(minimumCells - actualItems.size) { null }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(10),
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(displayItems.size) { index ->
            val chunk = displayItems[index]
            ChunkSquare(progress = chunk?.progress)
        }
    }
}

@Composable
private fun ChunkSquare(progress: Float?) {
    val normalized = progress?.coerceIn(0f, 1f) ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = normalized,
        animationSpec = tween(durationMillis = 600),
        label = "chunk-progress"
    )

    val hasProgress = progress != null
    val baseColor = if (hasProgress) {
        lerp(GridBaseColor, GridAccentColor, animatedProgress)
    } else {
        GridIdleColor
    }

    val backgroundModifier = if (hasProgress) {
        Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    baseColor.copy(alpha = 0.95f),
                    lerp(baseColor, Color.Black, 0.25f)
                )
            )
        )
    } else {
        Modifier.background(baseColor)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(backgroundModifier)
            .border(BorderStroke(1.dp, GridBorderColor))
    )
}

@Composable
private fun BottomHud(
    modifier: Modifier = Modifier,
    progress: Float,
    status: DownloadStatus,
    totalBytes: Long?,
    isDownloading: Boolean,
    onAddClick: () -> Unit
) {
    val progressPercent = (progress * 100f).coerceIn(0f, 100f).roundToInt()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = HudBackgroundColor.copy(alpha = 0.92f),
        shadowElevation = 8.dp
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                trackColor = Color.Transparent,
                color = HudGlowColor
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.95f),
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "$progressPercent%",
                            style = MaterialTheme.typography.bodySmall,
                            color = HudAccentColor
                        )
                        totalBytes?.let {
                            Text(
                                text = "· ${formatBytesForUi(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                
                IconButton(
                    onClick = onAddClick,
                    enabled = !isDownloading,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDownloading) 
                                Color.White.copy(alpha = 0.08f) 
                            else 
                                HudGlowColor.copy(alpha = 0.85f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add download",
                        tint = if (isDownloading) 
                            Color.White.copy(alpha = 0.35f) 
                        else 
                            Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun UrlInputDialog(
    url: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    errorMessage: String?,
    isDownloading: Boolean
) {
    val canConfirm = url.isNotBlank() && !isDownloading

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HudBackgroundColor,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Add download",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Link", color = Color.White.copy(alpha = 0.7f)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.White.copy(alpha = 0.4f),
                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color.White.copy(alpha = 0.8f),
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text("Send", color = if (canConfirm) Color.White else Color.White.copy(alpha = 0.5f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
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
            onDownloadClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
