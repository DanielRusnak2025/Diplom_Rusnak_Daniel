package org.example.androidapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Sampled from user screenshots 2026-11-10
private val NewsDovePalette = lightColorScheme(
    primary              = Teal,
    onPrimary            = Cream,
    primaryContainer     = Panel,
    onPrimaryContainer   = Teal,
    secondary            = Teal,
    onSecondary          = Cream,
    secondaryContainer   = Panel,
    onSecondaryContainer = Teal,
    background           = Cream,
    onBackground         = Teal,
    surface              = Cream,
    onSurface            = Teal,
    surfaceVariant       = Panel,
    onSurfaceVariant     = MutedText,
    error                = ErrorText,
    onError              = Cream,
    errorContainer       = DangerRed,
    onErrorContainer     = White,
    outline              = Teal,
    outlineVariant       = Panel,
)

@Composable
fun NewsDoveTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Cream.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Cream.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = NewsDovePalette,
        typography  = NewsDoveTypography,
        shapes      = NewsDoveShapes,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Decorative diagonal band: fills lower-right area, upper-left stays cream
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = Path()
                    path.moveTo(size.width * 0.4f, 0f)
                    path.lineTo(size.width, 0f)
                    path.lineTo(size.width, size.height)
                    path.lineTo(0f, size.height)
                    path.lineTo(0f, size.height * 0.4f)
                    path.close()
                    drawPath(path, DiagBand)
                }
                content()
            }
        }
    }
}
