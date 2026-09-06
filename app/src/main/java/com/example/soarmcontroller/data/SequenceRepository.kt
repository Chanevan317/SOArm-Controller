package com.example.soarmcontroller.data

import com.example.soarmcontroller.data.model.SeqStep
import com.example.soarmcontroller.data.model.SequenceRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Stores taught sequences locally, newest first. */
class SequenceRepository(private val store: JsonFileStore) {

    private val _sequences = MutableStateFlow(load())
    val sequences: StateFlow<List<SequenceRecord>> = _sequences.asStateFlow()

    suspend fun add(record: SequenceRecord) = persist(_sequences.value + record)

    suspend fun delete(id: String) = persist(_sequences.value.filterNot { it.id == id })

    private suspend fun persist(list: List<SequenceRecord>) {
        val sorted = list.sortedByDescending { it.createdAt }
        _sequences.value = sorted
        withContext(Dispatchers.IO) { store.writeArray(FILE, encode(sorted)) }
    }

    private fun load(): List<SequenceRecord> {
        val arr = store.readArray(FILE)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    SequenceRecord(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        steps = decodeSteps(o.optJSONArray("steps") ?: JSONArray()),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    ),
                )
            }
        }.sortedByDescending { it.createdAt }
    }

    private fun decodeSteps(arr: JSONArray): List<SeqStep> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            when (o.optString("t")) {
                "pose" -> add(SeqStep.Pose(o.optString("label", "Pose")))
                "delay" -> add(SeqStep.Delay(o.optInt("ms", 500)))
            }
        }
    }

    private fun encode(list: List<SequenceRecord>): JSONArray {
        val arr = JSONArray()
        list.forEach { rec ->
            val steps = JSONArray()
            rec.steps.forEach { step ->
                steps.put(
                    when (step) {
                        is SeqStep.Pose -> JSONObject().put("t", "pose").put("label", step.label)
                        is SeqStep.Delay -> JSONObject().put("t", "delay").put("ms", step.millis)
                    },
                )
            }
            arr.put(
                JSONObject().apply {
                    put("id", rec.id)
                    put("name", rec.name)
                    put("createdAt", rec.createdAt)
                    put("steps", steps)
                },
            )
        }
        return arr
    }

    private companion object {
        const val FILE = "sequences.json"
    }
}
