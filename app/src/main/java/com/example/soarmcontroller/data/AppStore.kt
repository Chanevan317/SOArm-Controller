package com.example.soarmcontroller.data

import android.content.Context

/**
 * Container for the local repositories. Create once at the app root and pass down.
 */
class AppStore(context: Context) {
    private val fileStore = JsonFileStore(context.applicationContext.filesDir)
    val presets = PresetRepository(fileStore)
    val sequences = SequenceRepository(fileStore)
}
