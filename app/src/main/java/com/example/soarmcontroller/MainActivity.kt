package com.example.soarmcontroller

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.example.soarmcontroller.data.AppStore
import com.example.soarmcontroller.data.Settings
import com.example.soarmcontroller.ui.SoArmApp
import com.example.soarmcontroller.ui.screens.WelcomeScreen
import com.example.soarmcontroller.ui.theme.SOArmControllerTheme
import com.example.soarmcontroller.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SoArmRoot() }
    }
}

@Composable
private fun SoArmRoot() {
    val appContext = LocalContext.current.applicationContext
    val settings = remember { Settings(appContext) }
    val store = remember { AppStore(appContext) }

    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var onboarded by remember { mutableStateOf(settings.onboarded) }

    val resolvedDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Keep the system bar icons legible against the current theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = !resolvedDark
            bars.isAppearanceLightNavigationBars = !resolvedDark
        }
    }

    SOArmControllerTheme(themeMode = themeMode) {
        AnimatedContent(
            targetState = onboarded,
            transitionSpec = {
                (fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 10 }) togetherWith
                    (fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 14 })
            },
            label = "welcome-to-app",
        ) { done ->
            if (!done) {
                WelcomeScreen(
                    onGetStarted = {
                        settings.onboarded = true
                        onboarded = true
                    },
                )
            } else {
                SoArmApp(
                    store = store,
                    themeMode = themeMode,
                    resolvedDark = resolvedDark,
                    onThemeModeChange = { mode ->
                        settings.themeMode = mode
                        themeMode = mode
                    },
                )
            }
        }
    }
}
