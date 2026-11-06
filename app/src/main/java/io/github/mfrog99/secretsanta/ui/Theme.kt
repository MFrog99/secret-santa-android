package io.github.mfrog99.secretsanta.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF2E6B4F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2F1CF),
    onSecondaryContainer = Color(0xFF002114),
    background = Color(0xFFFFFBF8),
    surface = Color(0xFFFFFBF8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AA),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF8C1D18),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFF96D5B3),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF115137),
    onSecondaryContainer = Color(0xFFB2F1CF),
)

@Composable
fun SecretSantaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
