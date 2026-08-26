package com.turingmirror.moetext.data

import android.content.Context
import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.engine.CustomReplace
import org.json.JSONArray
import org.json.JSONObject

object ConfigStore {

    private const val PREFS = "moetext_config"
    private const val KEY_REALTIME = "realtime_mode"
    private const val KEY_WO = "wo_to_benmiao"
    private const val KEY_NI = "ni_to_zhuren"
    private const val KEY_SUFFIX_ENABLED = "suffix_enabled"
    private const val KEY_SUFFIX_TEXT = "suffix_text"
    private const val KEY_TAIL_ENABLED = "tail_enabled"
    private const val KEY_TAIL_TEXT = "tail_text"
    private const val KEY_EMOTICON_ENABLED = "emoticon_enabled"
    private const val KEY_EMOTICONS = "emoticons"
    private const val KEY_CUSTOM_REPLACES = "custom_replaces"

    fun load(ctx: Context): AppConfig {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            AppConfig(
                realtimeMode = sp.getBoolean(KEY_REALTIME, false),
                woToBenmiao = sp.getBoolean(KEY_WO, true),
                niToZhuren = sp.getBoolean(KEY_NI, false),
                sentenceSuffixEnabled = sp.getBoolean(KEY_SUFFIX_ENABLED, true),
                sentenceSuffixText = sp.getString(KEY_SUFFIX_TEXT, "喵") ?: "喵",
                tailEnabled = sp.getBoolean(KEY_TAIL_ENABLED, false),
                tailText = sp.getString(KEY_TAIL_TEXT, "") ?: "",
                emoticonEnabled = sp.getBoolean(KEY_EMOTICON_ENABLED, true),
                emoticons = parseEmoticons(sp.getString(KEY_EMOTICONS, "")),
                customReplaces = parseReplaces(sp.getString(KEY_CUSTOM_REPLACES, ""))
            )
        } catch (e: Exception) {
            AppConfig()
        }
    }

    fun save(ctx: Context, config: AppConfig) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(KEY_REALTIME, config.realtimeMode)
            .putBoolean(KEY_WO, config.woToBenmiao)
            .putBoolean(KEY_NI, config.niToZhuren)
            .putBoolean(KEY_SUFFIX_ENABLED, config.sentenceSuffixEnabled)
            .putString(KEY_SUFFIX_TEXT, config.sentenceSuffixText)
            .putBoolean(KEY_TAIL_ENABLED, config.tailEnabled)
            .putString(KEY_TAIL_TEXT, config.tailText)
            .putBoolean(KEY_EMOTICON_ENABLED, config.emoticonEnabled)
            .putString(KEY_EMOTICONS, config.emoticons.joinToString("\n"))
            .putString(KEY_CUSTOM_REPLACES, encodeReplaces(config.customReplaces))
            .apply()
    }

    private fun parseEmoticons(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return AppConfig.BUILTIN_EMOTICONS
        val list = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        return list.ifEmpty { AppConfig.BUILTIN_EMOTICONS }
    }

    private fun parseReplaces(raw: String?): List<CustomReplace> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                CustomReplace(
                    enabled = o.optBoolean("enabled", true),
                    from = o.optString("from", ""),
                    to = o.optString("to", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun encodeReplaces(list: List<CustomReplace>): String {
        if (list.isEmpty()) return ""
        val arr = JSONArray()
        for (r in list) {
            arr.put(JSONObject().put("enabled", r.enabled).put("from", r.from).put("to", r.to))
        }
        return arr.toString()
    }
}
