package com.turingmirror.moetext.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1289F0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8F4FD),
    onPrimaryContainer = Color(0xFF1E242B),
    secondary = Color(0xFF5D6874),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4F6F8),
    onSecondaryContainer = Color(0xFF1E242B),
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF1E242B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E242B),
    surfaceVariant = Color(0xFFF4F6F8),
    onSurfaceVariant = Color(0xFF5D6874),
    outline = Color(0x241E242B),
    outlineVariant = Color(0x1A1E242B),
    error = Color(0xFFC0392B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4AA3F0),
    onPrimary = Color(0xFF0F1319),
    primaryContainer = Color(0xFF1E2837),
    onPrimaryContainer = Color(0xFFE4E9EF),
    secondary = Color(0xFFA9B3BE),
    onSecondary = Color(0xFF0F1319),
    secondaryContainer = Color(0xFF232933),
    onSecondaryContainer = Color(0xFFE4E9EF),
    background = Color(0xFF171B22),
    onBackground = Color(0xFFE4E9EF),
    surface = Color(0xFF1F242D),
    onSurface = Color(0xFFE4E9EF),
    surfaceVariant = Color(0xFF262C36),
    onSurfaceVariant = Color(0xFFA9B3BE),
    outline = Color(0x38E8F0F8),
    outlineVariant = Color(0x2EE8F0F8),
    error = Color(0xFFE57368)
)

@Composable
fun MoeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
