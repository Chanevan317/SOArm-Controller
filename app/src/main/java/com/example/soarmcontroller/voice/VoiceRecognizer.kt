package com.example.soarmcontroller.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/** UI-facing state for the Voice screen. */
data class VoiceState(
    val ready: Boolean = false,
    val listening: Boolean = false,
    val partial: String = "",
    val lastCommand: String? = null,
    val error: String? = null,
)

/**
 * Thin wrapper over Vosk: loads the on-disk model, runs streaming recognition
 * constrained to [commands], and exposes progress as [state].
 *
 * Recognised text is still just surfaced to the UI here — mapping a command to a
 * robot action happens once the transport is wired.
 */
class VoiceRecognizer(
    private val appContext: Context,
    private val commands: List<String>,
) : RecognitionListener {

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    /** Every recognised keyword, including immediate repeats of the same word. */
    private val _recognized = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val recognized: SharedFlow<String> = _recognized.asSharedFlow()

    private var model: Model? = null
    private var speech: SpeechService? = null

    /** Loads the model from disk if present. Safe to call repeatedly. */
    suspend fun ensureModel(): Boolean {
        if (model != null) {
            _state.value = _state.value.copy(ready = true)
            return true
        }
        if (!VoiceModel.isReady(appContext)) return false
        return try {
            model = withContext(Dispatchers.IO) {
                Model(VoiceModel.dir(appContext).absolutePath)
            }
            _state.value = _state.value.copy(ready = true, error = null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed", e)
            _state.value = _state.value.copy(error = "Model load failed")
            false
        }
    }

    fun start() {
        val m = model ?: return
        if (speech != null) return
        try {
            val grammar = commands.joinToString(prefix = "[", postfix = ", \"[unk]\"]") { "\"$it\"" }
            val recognizer = Recognizer(m, SAMPLE_RATE, grammar)
            speech = SpeechService(recognizer, SAMPLE_RATE).also { it.startListening(this) }
            _state.value = _state.value.copy(listening = true, partial = "", error = null)
        } catch (e: Exception) {
            Log.e(TAG, "start() failed", e)
            _state.value = _state.value.copy(error = e.message ?: "Could not start", listening = false)
        }
    }

    fun stop() {
        speech?.stop()
        speech = null
        _state.value = _state.value.copy(listening = false, partial = "")
    }

    fun shutdown() {
        speech?.shutdown()
        speech = null
        model?.close()
        model = null
    }

    // --- RecognitionListener --------------------------------------------
    override fun onPartialResult(hypothesis: String?) {
        val p = hypothesis?.let { runCatching { JSONObject(it).optString("partial") }.getOrNull() }
        if (!p.isNullOrBlank()) _state.value = _state.value.copy(partial = p)
    }

    override fun onResult(hypothesis: String?) {
        val t = hypothesis?.let { runCatching { JSONObject(it).optString("text") }.getOrNull() }
        if (!t.isNullOrBlank()) {
            _state.value = _state.value.copy(lastCommand = t, partial = "")
            _recognized.tryEmit(t)
        }
    }

    override fun onFinalResult(hypothesis: String?) = onResult(hypothesis)

    override fun onError(e: Exception?) {
        Log.e(TAG, "recognition error", e)
        _state.value = _state.value.copy(error = e?.message ?: "Recognition error", listening = false)
    }

    override fun onTimeout() {
        _state.value = _state.value.copy(listening = false)
    }

    private companion object {
        const val TAG = "VoiceRecognizer"
        const val SAMPLE_RATE = 16_000.0f
    }
}
