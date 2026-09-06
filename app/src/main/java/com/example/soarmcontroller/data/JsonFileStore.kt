package com.example.soarmcontroller.data

import android.util.Log
import org.json.JSONArray
import java.io.File

/**
 * Minimal local persistence: one JSON file per collection under the app's private
 * files dir, rewritten atomically on every change. No external dependencies and
 * no annotation processing — deliberately simple for the current shell.
 *
 * The repositories are the app's contract; this file store can be swapped for
 * Room later without touching the UI.
 */
class JsonFileStore(private val dir: File) {

    fun readArray(fileName: String): JSONArray =
        try {
            val f = File(dir, fileName)
            if (f.exists()) JSONArray(f.readText()) else JSONArray()
        } catch (e: Exception) {
            Log.w(TAG, "readArray($fileName) failed, starting empty", e)
            JSONArray()
        }

    fun writeArray(fileName: String, array: JSONArray) {
        try {
            val target = File(dir, fileName)
            val tmp = File(dir, "$fileName.tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(target)) {
                target.writeText(array.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeArray($fileName) failed", e)
        }
    }

    private companion object {
        const val TAG = "JsonFileStore"
    }
}
