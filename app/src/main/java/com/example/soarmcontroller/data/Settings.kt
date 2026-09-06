package com.example.soarmcontroller.data

import android.content.Context
import androidx.core.content.edit
import com.example.soarmcontroller.ui.theme.ThemeMode

/**
 * Small key–value settings backed by SharedPreferences: the onboarding flag and
 * the user's theme choice. Read on the main thread — the store is tiny.
 */
class Settings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("soarm_settings", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDED, value) }

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit { putString(KEY_THEME, value.name) }

    private companion object {
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_THEME = "theme_mode"
    }
}
