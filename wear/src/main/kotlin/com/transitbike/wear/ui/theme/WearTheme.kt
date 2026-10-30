package com.transitbike.wear.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

val EmeraldPrimary = Color(0xFF10B981)
val EmeraldOnPrimary = Color(0xFF003822)
val BackgroundDark = Color(0xFF000000) // Deep black for OLED watch battery preservation
val SurfaceDark = Color(0xFF18181B)
val OnSurfaceDark = Color(0xFFFFFFFF)

private val DefaultWearColorScheme = ColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    background = BackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark
)

@Composable
fun WearAppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        dynamicColorScheme(context) ?: DefaultWearColorScheme
    } else {
        DefaultWearColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
