package com.personal.cardvault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

object VaultColors {
    val Background = Color(0xFF0D0E12)
    val Surface = Color(0xFF16181E)
    val SurfaceHigh = Color(0xFF1E2028)
    val SurfaceHighest = Color(0xFF272A34)
    val Outline = Color(0xFF2E313C)
    val Gold = Color(0xFFE3B964)
    val GoldDim = Color(0xFF3A2F14)
    val Text = Color(0xFFF2F2F5)
    val TextMuted = Color(0xFF9A9CA8)
    val Danger = Color(0xFFFF6B6B)
    val Warning = Color(0xFFF5B54A)
    val Success = Color(0xFF5FD39A)
}

private val colors = darkColorScheme(
    primary = VaultColors.Gold,
    onPrimary = Color(0xFF241A04),
    primaryContainer = VaultColors.GoldDim,
    onPrimaryContainer = VaultColors.Gold,
    secondary = Color(0xFFBFC2CF),
    onSecondary = Color(0xFF15171C),
    secondaryContainer = VaultColors.SurfaceHighest,
    onSecondaryContainer = VaultColors.Text,
    background = VaultColors.Background,
    onBackground = VaultColors.Text,
    surface = VaultColors.Background,
    onSurface = VaultColors.Text,
    surfaceVariant = VaultColors.SurfaceHigh,
    onSurfaceVariant = VaultColors.TextMuted,
    surfaceContainerLowest = Color(0xFF0A0B0E),
    surfaceContainerLow = Color(0xFF121318),
    surfaceContainer = VaultColors.Surface,
    surfaceContainerHigh = VaultColors.SurfaceHigh,
    surfaceContainerHighest = VaultColors.SurfaceHighest,
    outline = VaultColors.Outline,
    outlineVariant = Color(0xFF23252E),
    error = VaultColors.Danger,
    onError = Color.Black,
    errorContainer = Color(0xFF3B1517),
    onErrorContainer = Color(0xFFFFB4B0),
)

private val base = Typography()
private val typography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun CardVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
