package com.example.soarmcontroller.data

import com.example.soarmcontroller.data.model.GoToPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Stores Go To presets locally, newest first. */
class PresetRepository(private val store: JsonFileStore) {

    private val _presets = MutableStateFlow(load())
    val presets: StateFlow<List<GoToPreset>> = _presets.asStateFlow()

    suspend fun add(preset: GoToPreset) = persist(_presets.value + preset)

    suspend fun delete(id: String) = persist(_presets.value.filterNot { it.id == id })

    private suspend fun persist(list: List<GoToPreset>) {
        val sorted = list.sortedByDescending { it.createdAt }
        _presets.value = sorted
        withContext(Dispatchers.IO) { store.writeArray(FILE, encode(sorted)) }
    }

    private fun load(): List<GoToPreset> {
        val arr = store.readArray(FILE)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    GoToPreset(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        x = o.optDoubleOrNull("x"),
                        y = o.optDoubleOrNull("y"),
                        z = o.optDoubleOrNull("z"),
                        pitch = o.optDoubleOrNull("pitch"),
                        roll = o.optDoubleOrNull("roll"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    ),
                )
            }
        }.sortedByDescending { it.createdAt }
    }

    private fun encode(list: List<GoToPreset>): JSONArray {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    p.x?.let { put("x", it) }
                    p.y?.let { put("y", it) }
                    p.z?.let { put("z", it) }
                    p.pitch?.let { put("pitch", it) }
                    p.roll?.let { put("roll", it) }
                    put("createdAt", p.createdAt)
                },
            )
        }
        return arr
    }

    private companion object {
        const val FILE = "goto_presets.json"
    }
}

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null
