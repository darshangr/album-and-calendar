package com.familyhub.display.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2D6A4F),
    onPrimary = Color.White,
    secondary = Color(0xFF40916C),
    tertiary = Color(0xFF52B788),
    background = Color(0xFFF8FAF9),
    surface = Color.White,
    onSurface = Color(0xFF1B4332),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95D5B2),
    onPrimary = Color(0xFF081C15),
    secondary = Color(0xFF74C69D),
    tertiary = Color(0xFFB7E4C7),
    background = Color(0xFF081C15),
    surface = Color(0xFF1B4332),
    onSurface = Color(0xFFD8F3DC),
)

val EventBirthday = Color(0xFFE76F51)
val EventParty = Color(0xFF9B5DE5)
val EventDefault = Color(0xFF2A9D8F)
val EventClass = Color(0xFF457B9D)
val EventOther = Color(0xFF6C757D)

@Composable
fun FamilyHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
