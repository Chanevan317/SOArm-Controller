package com.example.soarmcontroller.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manages the offline Vosk model on disk. The model isn't bundled in the APK
 * (~40 MB) — it's fetched once on first use and unpacked into filesDir.
 */
object VoiceModel {

    const val NAME = "vosk-model-small-en-us-0.15"
    private const val URL = "https://alphacephei.com/vosk/models/$NAME.zip"
    private const val TAG = "VoiceModel"

    fun dir(context: Context): File = File(context.filesDir, NAME)

    /** A rough "is it usable" check — the acoustic model folder must be present. */
    fun isReady(context: Context): Boolean =
        File(dir(context), "am/final.mdl").exists() ||
            File(dir(context), "am").let { it.isDirectory && it.list()?.isNotEmpty() == true }

    /**
     * Downloads and unpacks the model. [onProgress] reports 0f..1f for the
     * download phase. Throws on failure; the caller catches.
     */
    suspend fun download(context: Context, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val zip = File(context.cacheDir, "$NAME.zip")
            try {
                fetch(zip, onProgress)
                unzipInto(zip, context.filesDir)
                if (!isReady(context)) error("Model unpacked but looks incomplete")
            } finally {
                zip.delete()
            }
        }

    private fun fetch(target: File, onProgress: (Float) -> Unit) {
        val conn = (URL(URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            error("Download failed: HTTP ${conn.responseCode}")
        }
        val total = conn.contentLengthLong.takeIf { it > 0 }
        conn.inputStream.use { input ->
            target.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                var done = 0L
                while (input.read(buf).also { read = it } >= 0) {
                    output.write(buf, 0, read)
                    done += read
                    if (total != null) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        onProgress(1f)
    }

    private fun unzipInto(zip: File, destRoot: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(destRoot, entry.name)
                if (!out.canonicalPath.startsWith(destRoot.canonicalPath + File.separator)) {
                    throw SecurityException("Zip entry escapes target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        Log.i(TAG, "Model unpacked into ${destRoot.absolutePath}")
    }
}
