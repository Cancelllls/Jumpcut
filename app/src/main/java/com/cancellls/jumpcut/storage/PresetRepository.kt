package com.cancellls.jumpcut.storage

import android.content.Context
import android.content.SharedPreferences
import com.cancellls.jumpcut.model.CreatorPreset
import com.cancellls.jumpcut.model.CutSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class PresetRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jumpcut_presets", Context.MODE_PRIVATE)

    private val builtInPresets = listOf(
        CreatorPreset(
            id = "preset_shorts",
            name = "Shorts / TikTok",
            settings = CutSettings(silenceThresholdDb = -30f, minSilenceDurationMs = 250L, paddingMs = 40L),
            isBuiltIn = true
        ),
        CreatorPreset(
            id = "preset_podcast",
            name = "Podcast Studio",
            settings = CutSettings(silenceThresholdDb = -34f, minSilenceDurationMs = 450L, paddingMs = 70L),
            isBuiltIn = true
        ),
        CreatorPreset(
            id = "preset_lecture",
            name = "Fast Lecture",
            settings = CutSettings(silenceThresholdDb = -28f, minSilenceDurationMs = 200L, paddingMs = 30L),
            isBuiltIn = true
        ),
        CreatorPreset(
            id = "preset_vlog",
            name = "Vlog Natural",
            settings = CutSettings(silenceThresholdDb = -32f, minSilenceDurationMs = 350L, paddingMs = 50L),
            isBuiltIn = true
        )
    )

    private val _presets = MutableStateFlow<List<CreatorPreset>>(loadAllPresets())
    val presets: StateFlow<List<CreatorPreset>> = _presets.asStateFlow()

    private fun loadAllPresets(): List<CreatorPreset> {
        val result = mutableListOf<CreatorPreset>()
        result.addAll(builtInPresets)

        val jsonStr = prefs.getString(KEY_CUSTOM_PRESETS, null) ?: return result
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val threshold = obj.getDouble("threshold").toFloat()
                val minDuration = obj.getLong("minDuration")
                val padding = obj.getLong("padding")
                val removeNoise = obj.optBoolean("removeNoise", true)
                val volumeBoost = obj.optDouble("volumeBoost", 1.0).toFloat()

                result.add(
                    CreatorPreset(
                        id = id,
                        name = name,
                        settings = CutSettings(
                            silenceThresholdDb = threshold,
                            minSilenceDurationMs = minDuration,
                            paddingMs = padding,
                            removeNoise = removeNoise,
                            volumeBoost = volumeBoost
                        ),
                        isBuiltIn = false
                    )
                )
            }
        } catch (_: Exception) {}

        return result
    }

    fun saveCustomPreset(name: String, settings: CutSettings): CreatorPreset {
        val id = "custom_${System.currentTimeMillis()}"
        val newPreset = CreatorPreset(id = id, name = name, settings = settings, isBuiltIn = false)

        val currentList = _presets.value.toMutableList()
        currentList.add(newPreset)
        _presets.value = currentList
        persistCustomPresets()
        return newPreset
    }

    fun deletePreset(id: String) {
        val currentList = _presets.value.toMutableList()
        currentList.removeAll { it.id == id && !it.isBuiltIn }
        _presets.value = currentList
        persistCustomPresets()
    }

    private fun persistCustomPresets() {
        val customOnly = _presets.value.filter { !it.isBuiltIn }
        val array = JSONArray()
        for (p in customOnly) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("threshold", p.settings.silenceThresholdDb.toDouble())
            obj.put("minDuration", p.settings.minSilenceDurationMs)
            obj.put("padding", p.settings.paddingMs)
            obj.put("removeNoise", p.settings.removeNoise)
            obj.put("volumeBoost", p.settings.volumeBoost.toDouble())
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_PRESETS, array.toString()).apply()
    }

    companion object {
        private const val KEY_CUSTOM_PRESETS = "custom_creator_presets"
    }
}
