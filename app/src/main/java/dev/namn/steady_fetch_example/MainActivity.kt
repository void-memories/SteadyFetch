package dev.namn.steady_fetch_example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.namn.steady_fetch.datamodels.DownloadStatus
import dev.namn.steady_fetch_example.ui.theme.SteadyFetchExampleTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Classic Matrix theme
private val TerminalBg = Color(0xFF000000) // Pure black
private val MatrixGreen = Color(0xFF00FF41) // Classic Matrix green
private val MatrixGreenBright = Color(0xFF39FF14) // Bright Matrix green
private val MatrixGreenDim = Color(0xFF00AA00) // Dim Matrix green
private val TerminalError = Color(0xFFFF0044) // Red
private val TerminalBorder = Color(0xFF00FF41).copy(alpha = 0.4f)
private val ButtonMinHeight = 56.dp
private val DemoDownloadLinks = listOf(
    "https://nbg1-speed.hetzner.com/100MB.bin",
    "https://speed.hetzner.de/100MB.bin",
    "https://speed.hetzner.de/1GB.bin"
)

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
            .background(TerminalBg)
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Terminal header
            TerminalHeader()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Chunk matrix
            ChunkMatrix(
                chunks = uiState.chunkProgress,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
        )

        }
        
        // Standalone download button/status counter at bottom
        DownloadButtonOrStatus(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.BottomCenter),
            progress = progress,
            status = status,
            isDownloading = uiState.isDownloading,
            onAddClick = { showUrlDialog = true }
        )

        VintageScanlineOverlay(
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showUrlDialog) {
        TerminalInputDialog(
            url = uiState.url,
            onUrlChange = onUrlChange,
            onDismiss = { showUrlDialog = false },
            onConfirm = {
                showUrlDialog = false
                onDownloadClick()
            },
            errorMessage = uiState.errorMessage,
            isDownloading = uiState.isDownloading,
            suggestedUrls = DemoDownloadLinks
        )
    }
}

@Composable
private fun ChunkMatrix(chunks: List<ChunkProgressUi>, modifier: Modifier = Modifier) {
    if (chunks.isEmpty()) {
        TerminalEmptyState(modifier = modifier)
    } else {
    val minimumCells = 20
    val actualItems = chunks.map { it as ChunkProgressUi? }
    val displayItems: List<ChunkProgressUi?> = when {
        actualItems.size >= minimumCells -> actualItems
        else -> actualItems + List(minimumCells - actualItems.size) { null }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(10),
        modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(displayItems.size) { index ->
            val chunk = displayItems[index]
                if (chunk != null) {
                    TerminalChunk(
                        progress = chunk.progress,
                        status = chunk.status
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalChunk(
    progress: Float,
    status: DownloadStatus
) {
    val normalized = progress.coerceIn(0f, 1f)
    val blockyProgress = (normalized * 8f).roundToInt() / 8f

    val isRunning = status == DownloadStatus.RUNNING
    val fillColor = if (isRunning) MatrixGreenBright else MatrixGreen
    val borderColor = if (isRunning) MatrixGreenBright else MatrixGreen.copy(alpha = 0.6f)
    val bgColor = if (isRunning) MatrixGreenBright.copy(alpha = 0.1f) else TerminalBg

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(bgColor)
            .border(
                BorderStroke(
                    width = 2.dp,
                    color = borderColor
                )
            )
    ) {
        val fillHeight = maxHeight * blockyProgress
        
        // 8-bit style solid fill from bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fillHeight)
                .align(Alignment.BottomStart)
                .background(fillColor)
        )
        
        // Progress percentage text
        Text(
            text = if (blockyProgress > 0.01f) "${(blockyProgress * 100).toInt()}%" else "",
            color = if (isRunning) MatrixGreenBright else MatrixGreen,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun TerminalEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(TerminalBg)
                    .border(BorderStroke(2.dp, MatrixGreen))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MatrixGreen.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "NO ACTIVE TRANSFERS",
                        style = MaterialTheme.typography.titleMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Click DOWNLOAD to initiate transfer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MatrixGreenDim,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalHeader() {
    var glowIndex by remember { mutableStateOf(0) }
    var cursorOn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            glowIndex = (glowIndex + 1) % 2
            delay(240)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            cursorOn = !cursorOn
            delay(320)
        }
    }

    val glowAlpha = if (glowIndex == 0) 0.25f else 0.6f
    val cursorAlpha = if (cursorOn) 1f else 0.2f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalBg)
            .border(BorderStroke(2.dp, MatrixGreen.copy(alpha = glowAlpha)))
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "STEADY_FETCH",
                        color = MatrixGreenBright,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "_",
                        color = MatrixGreenBright.copy(alpha = cursorAlpha),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "v1.0 • DOWNLOAD SYSTEM",
                    color = MatrixGreenDim,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .background(MatrixGreenBright.copy(alpha = glowAlpha))
                    .border(BorderStroke(1.dp, MatrixGreenBright))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "ONLINE",
                    color = MatrixGreenBright.copy(alpha = cursorAlpha),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun TerminalStatusBar(
    progress: Float,
    status: DownloadStatus,
    isDownloading: Boolean
) {
    val progressPercent = (progress * 100f).coerceIn(0f, 100f).roundToInt()
    val progressBar = "█".repeat((progressPercent / 5).coerceAtMost(20))
    val statusColor = when (status) {
        DownloadStatus.RUNNING -> MatrixGreenBright
        DownloadStatus.SUCCESS -> MatrixGreen
        DownloadStatus.FAILED -> TerminalError
        else -> MatrixGreenDim
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalBg)
            .border(BorderStroke(2.dp, MatrixGreen))
            .padding(16.dp)
    ) {
        Text(
            text = "> status: ${status.name.lowercase()}",
            color = statusColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "> progress: [$progressBar] $progressPercent%",
            color = MatrixGreen,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun DownloadButtonOrStatus(
    modifier: Modifier = Modifier,
    progress: Float,
    status: DownloadStatus,
    isDownloading: Boolean,
    onAddClick: () -> Unit
) {
    AnimatedContent(
        targetState = isDownloading,
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 90, easing = LinearEasing))
                .togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 60, easing = LinearEasing))
                )
        },
        contentAlignment = Alignment.Center,
        label = "download-button-state"
    ) { downloading ->
        if (downloading) {
            StatusLoaderButton(
                modifier = Modifier.fillMaxWidth(),
                progress = progress,
                status = status
            )
        } else {
            DownloadButton(
                modifier = Modifier.fillMaxWidth(),
                onAddClick = onAddClick
            )
        }
    }
}

@Composable
private fun StatusLoaderButton(
    modifier: Modifier = Modifier,
    progress: Float,
    status: DownloadStatus
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val progressPercent = (clampedProgress * 100f).roundToInt()
    val statusText = when (status) {
        DownloadStatus.RUNNING -> "transferring packets"
        DownloadStatus.QUEUED -> "queued for dispatch"
        DownloadStatus.SUCCESS -> "download complete"
        DownloadStatus.FAILED -> "transmission failed"
    }
    val statusColor = when (status) {
        DownloadStatus.RUNNING -> MatrixGreenBright
        DownloadStatus.SUCCESS -> MatrixGreen
        DownloadStatus.FAILED -> TerminalError
        DownloadStatus.QUEUED -> MatrixGreenDim
    }
    var loaderStep by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            loaderStep = (loaderStep + 1) % 8
            delay(140)
        }
    }
    val loaderPulse = if (loaderStep % 2 == 0) 0.08f else 0.18f
    val shimmerOffset = (loaderStep % 4) / 4f

    Box(
        modifier = modifier
            .height(ButtonMinHeight)
            .border(BorderStroke(2.dp, MatrixGreenBright))
            .background(TerminalBg)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MatrixGreenBright.copy(alpha = loaderPulse))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clampedProgress)
                    .background(MatrixGreenBright.copy(alpha = loaderPulse + 0.25f))
            )
            val shimmerWidth = 0.22f
            val shimmerStart = shimmerOffset
            val shimmerEnd = (shimmerStart + shimmerWidth).coerceAtMost(1f)
            if (shimmerEnd > shimmerStart) {
                val startPadding = maxWidth * shimmerStart
                val shimmerWidthDp = maxWidth * (shimmerEnd - shimmerStart)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(shimmerWidthDp)
                        .offset(x = startPadding)
                        .background(MatrixGreenBright.copy(alpha = 0.45f))
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                    Text(
                    text = "> $statusText",
                    color = statusColor,
                        style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                    )
            Text(
                text = "${progressPercent}%",
                color = MatrixGreenBright,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
                        )
                    }
                }
}

@Composable
private fun DownloadButton(
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit
) {
    var brightFlash by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            brightFlash = !brightFlash
            delay(260)
        }
    }
    val idlePulse = if (brightFlash) 0.22f else 0.12f

    Box(
        modifier = modifier
            .clickable(onClick = onAddClick)
            .height(ButtonMinHeight)
            .background(MatrixGreenBright.copy(alpha = idlePulse))
            .border(BorderStroke(2.dp, MatrixGreenBright))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Download",
                tint = MatrixGreenBright,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = "DOWNLOAD",
                color = MatrixGreenBright,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
                    )
                }
            }
        }


@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TerminalInputDialog(
    url: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    errorMessage: String?,
    isDownloading: Boolean,
    suggestedUrls: List<String>
) {
    val canConfirm = url.isNotBlank() && !isDownloading

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TerminalBg,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "┌─────────────────────────────┐\n│  INITIATE DOWNLOAD SEQUENCE │\n└─────────────────────────────┘",
                    color = MatrixGreenBright,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "> download --url",
                    color = MatrixGreen,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = MatrixGreenBright,
                        unfocusedIndicatorColor = TerminalBorder,
                        cursorColor = MatrixGreenBright,
                        focusedTextColor = MatrixGreen,
                        unfocusedTextColor = MatrixGreenDim,
                        focusedContainerColor = TerminalBg,
                        unfocusedContainerColor = TerminalBg
                    )
                )
                errorMessage?.let { error ->
                    Text(
                        text = "> ERROR: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = TerminalError,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (suggestedUrls.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "> available sources:",
                            color = MatrixGreenDim,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        suggestedUrls.forEach { suggestion ->
                            Text(
                                text = "  • $suggestion",
                                color = MatrixGreen,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.clickable { onUrlChange(suggestion) },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text(
                    "> execute",
                    color = if (canConfirm) MatrixGreenBright else MatrixGreenDim,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "> cancel",
                    color = MatrixGreenDim,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun VintageScanlineOverlay(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val lineHeightPx = remember(density) { with(density) { 1.dp.toPx() } }
    val spacingPx = remember(density) { with(density) { 6.dp.toPx() } }
    var offsetPx by remember { mutableStateOf(0f) }

    LaunchedEffect(lineHeightPx, spacingPx) {
        while (true) {
            offsetPx = (offsetPx + lineHeightPx) % spacingPx
            delay(90)
        }
    }

    Canvas(modifier = modifier) {
        var y = -offsetPx
        while (y < size.height) {
            drawRect(
                color = MatrixGreen.copy(alpha = 0.04f),
                topLeft = Offset(0f, y),
                size = Size(width = size.width, height = lineHeightPx)
            )
            y += spacingPx
            }
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
            chunkProgress = listOf(
                ChunkProgressUi("chunk-1", 0, 0.4f, DownloadStatus.RUNNING),
                ChunkProgressUi("chunk-2", 1, 0.9f, DownloadStatus.RUNNING),
                ChunkProgressUi("chunk-3", 2, 0.2f, DownloadStatus.RUNNING)
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
