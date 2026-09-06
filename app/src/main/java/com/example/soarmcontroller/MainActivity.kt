package com.example.soarmcontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.soarmcontroller.data.AppStore
import com.example.soarmcontroller.ui.SoArmApp
import com.example.soarmcontroller.ui.theme.SOArmControllerTheme
import com.example.soarmcontroller.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SoArmRoot() }
    }
}

@Composable
private fun SoArmRoot() {
    val appContext = LocalContext.current.applicationContext
    val store = remember { AppStore(appContext) }

    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
    val systemDark = isSystemInDarkTheme()
    val resolvedDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    SOArmControllerTheme(themeMode = themeMode) {
        SoArmApp(
            store = store,
            resolvedDark = resolvedDark,
            onToggleTheme = {
                themeMode = if (resolvedDark) ThemeMode.LIGHT else ThemeMode.DARK
            },
        )
    }
}
