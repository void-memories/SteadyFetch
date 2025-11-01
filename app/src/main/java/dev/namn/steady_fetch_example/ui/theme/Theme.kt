package dev.namn.steady_fetch_example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NeonDarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = DeepSpace,
    secondary = NeonCyan,
    onSecondary = DeepSpace,
    tertiary = NeonMagenta,
    onTertiary = DeepSpace,
    background = DeepSpace,
    onBackground = SoftWhite,
    surface = MidnightSurface,
    onSurface = SoftWhite,
    surfaceVariant = SlateGrey,
    onSurfaceVariant = SoftWhite.copy(alpha = 0.8f),
    outline = NeonCyan.copy(alpha = 0.6f)
)

@Composable
fun SteadyFetchExampleTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeonDarkColorScheme,
        typography = Typography,
        content = content
    )
}
