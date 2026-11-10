package org.example.androidapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NewsDovePalette = lightColorScheme(
    primary              = DarkGreen,
    onPrimary            = White,
    primaryContainer     = DarkOlive,
    onPrimaryContainer   = White,
    secondary            = Highlight,
    onSecondary          = White,
    secondaryContainer   = Tan,
    onSecondaryContainer = DarkBrown,
    background           = Tan,
    onBackground         = DarkBrown,
    surface              = Cream,
    onSurface            = DarkBrown,
    surfaceVariant       = Cream,
    onSurfaceVariant     = OliveText,
    error                = DeepRed,
    onError              = White,
    errorContainer       = ErrorContainer,
    onErrorContainer     = OnErrorContainer,
    outline              = DarkGreen,
    outlineVariant       = Highlight,
)

@Composable
fun NewsDoveTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Tan.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Tan.toArgb()
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
            content()
        }
    }
}
