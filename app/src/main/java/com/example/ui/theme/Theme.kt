package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CineProColorScheme = darkColorScheme(
    primary = CineAmber,
    onPrimary = ObsidianBlack,
    primaryContainer = CineAmberDim,
    onPrimaryContainer = CineAmberLight,
    secondary = CyanAnamorphic,
    onSecondary = ObsidianBlack,
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFA5F3FC),
    tertiary = EmeraldFocus,
    onTertiary = ObsidianBlack,
    error = TallyRed,
    onError = Color.White,
    errorContainer = TallyRedDark,
    onErrorContainer = Color(0xFFFECACA),
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = Color(0xFF12151C),
    onSurface = TextPrimary,
    surfaceVariant = SlateSurface,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CineProColorScheme,
        typography = Typography,
        content = content
    )
}
