package com.speakdrive.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat


// --- Brand Colors: Electric Indigo / Royal Blue & Warm Amber Accent ---
private val ElectricBlue = Color(0xFF2563EB)
private val ElectricBlueDark = Color(0xFF60A5FA)
private val IceBlue = Color(0xFFEFF6FF)
private val NavyContainerDark = Color(0xFF1E293B)

private val AmberFlame = Color(0xFFF59E0B)
private val AmberFlameDark = Color(0xFFFBBF24)
private val AmberContainerLight = Color(0xFFFEF3C7)
private val AmberContainerDark = Color(0xFF2D2313)

private val EmeraldMint = Color(0xFF10B981)
private val EmeraldMintDark = Color(0xFF34D399)
private val EmeraldContainerLight = Color(0xFFD1FAE5)
private val EmeraldContainerDark = Color(0xFF132E27)

// --- Extended Signal & Aura Colors ---
object AppColors {
    val StreakOrange = Color(0xFFF97316)
    val CorrectGreen = Color(0xFF10B981)
    val ErrorRed = Color(0xFFEF4444)
    val AiViolet = Color(0xFF8B5CF6)
    val AiCyan = Color(0xFF06B6D4)

    val LiveAuraBrush = Brush.sweepGradient(
        listOf(
            Color(0xFF8B5CF6),
            Color(0xFF06B6D4),
            Color(0xFF3B82F6),
            Color(0xFF8B5CF6)
        )
    )

    val PrimaryGradient = Brush.linearGradient(
        listOf(
            Color(0xFF2563EB),
            Color(0xFF3B82F6)
        )
    )

    val CardDarkBorder = Color(0xFF1E293B)
    val CardLightBorder = Color(0xFFE2E8F0)
}

private val LightColors = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = IceBlue,
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = AmberFlame,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = Color(0xFF92400E),
    tertiary = EmeraldMint,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = Color(0xFF065F46),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

private val DarkColors = darkColorScheme(
    primary = ElectricBlueDark,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = NavyContainerDark,
    onPrimaryContainer = Color(0xFFBFDBFE),
    secondary = AmberFlameDark,
    onSecondary = Color(0xFF451A03),
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = EmeraldMintDark,
    onTertiary = Color(0xFF064E3B),
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = Color(0xFF0B0F19),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF3B1219),
    onErrorContainer = Color(0xFFFECDD3)
)

@Composable
fun SpeakDriveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Edge-to-edge: the status bar is transparent, icon colour follows theme appearance.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

