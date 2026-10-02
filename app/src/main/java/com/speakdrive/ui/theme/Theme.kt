package com.speakdrive.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Brand colours: a calm road-blue with a traffic-sign amber accent.
private val Blue40 = Color(0xFF1F4FC4)
private val Blue80 = Color(0xFFB3C5FF)
private val Blue90 = Color(0xFFDBE1FF)
private val Blue20 = Color(0xFF00287A)
private val Amber40 = Color(0xFF8A5100)
private val Amber80 = Color(0xFFFFB86E)
private val Amber90 = Color(0xFFFFDCBE)
private val Teal40 = Color(0xFF006A60)
private val Teal80 = Color(0xFF53DBC9)

private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Color(0xFF001551),
    secondary = Amber40,
    onSecondary = Color.White,
    secondaryContainer = Amber90,
    onSecondaryContainer = Color(0xFF2C1600),
    tertiary = Teal40,
    onTertiary = Color.White,
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F)
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Color(0xFF003AAB),
    onPrimaryContainer = Blue90,
    secondary = Amber80,
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF693C00),
    onSecondaryContainer = Amber90,
    tertiary = Teal80,
    onTertiary = Color(0xFF003731),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0)
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
            // Edge-to-edge: the status bar is transparent, only its icon colour needs to follow the theme.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
