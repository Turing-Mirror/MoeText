package com.turingmirror.moetext.data

import android.content.Context
import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.engine.CustomReplace
import com.turingmirror.moetext.engine.PickMode
import org.json.JSONArray
import org.json.JSONObject

object ConfigStore {

    private const val PREFS = "moetext_config"
    private const val KEY_REALTIME = "realtime_mode"
    private const val KEY_WO = "wo_to_benmiao"
    private const val KEY_NI = "ni_to_zhuren"
    private const val KEY_WOMEN = "women_to_benmiaomen"
    private const val KEY_NIMEN = "nimen_to_zhurenmen"
    private const val KEY_SUFFIX_ENABLED = "suffix_enabled"
    private const val KEY_SUFFIX_LIST = "suffix_list"
    private const val KEY_SUFFIX_PICK = "suffix_pick"
    private const val KEY_TAIL_ENABLED = "tail_enabled"
    private const val KEY_TAIL_LIST = "tail_list"
    private const val KEY_TAIL_PICK = "tail_pick"
    private const val KEY_EMOTICON_ENABLED = "emoticon_enabled"
    private const val KEY_EMOTICONS = "emoticons"
    private const val KEY_CUSTOM_REPLACES = "custom_replaces"
    private const val LEGACY_KEY_SUFFIX_TEXT = "suffix_text"
    private const val LEGACY_KEY_TAIL_TEXT = "tail_text"

    fun load(ctx: Context): AppConfig {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            AppConfig(
                realtimeMode = sp.getBoolean(KEY_REALTIME, false),
                woToBenmiao = sp.getBoolean(KEY_WO, true),
                niToZhuren = sp.getBoolean(KEY_NI, false),
                woMenToBenmiaoMen = sp.getBoolean(KEY_WOMEN, false),
                niMenToZhurenMen = sp.getBoolean(KEY_NIMEN, false),
                sentenceSuffixEnabled = sp.getBoolean(KEY_SUFFIX_ENABLED, true),
                sentenceSuffixes = parseList(sp, KEY_SUFFIX_LIST, LEGACY_KEY_SUFFIX_TEXT, listOf("喵")),
                sentenceSuffixPick = parsePick(sp, KEY_SUFFIX_PICK, PickMode.SEQUENTIAL),
                tailEnabled = sp.getBoolean(KEY_TAIL_ENABLED, false),
                tails = parseList(sp, KEY_TAIL_LIST, LEGACY_KEY_TAIL_TEXT, emptyList()),
                tailPick = parsePick(sp, KEY_TAIL_PICK, PickMode.SEQUENTIAL),
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
            .putBoolean(KEY_WOMEN, config.woMenToBenmiaoMen)
            .putBoolean(KEY_NIMEN, config.niMenToZhurenMen)
            .putBoolean(KEY_SUFFIX_ENABLED, config.sentenceSuffixEnabled)
            .putString(KEY_SUFFIX_LIST, config.sentenceSuffixes.joinToString("\n"))
            .putString(KEY_SUFFIX_PICK, config.sentenceSuffixPick.name)
            .putBoolean(KEY_TAIL_ENABLED, config.tailEnabled)
            .putString(KEY_TAIL_LIST, config.tails.joinToString("\n"))
            .putString(KEY_TAIL_PICK, config.tailPick.name)
            .putBoolean(KEY_EMOTICON_ENABLED, config.emoticonEnabled)
            .putString(KEY_EMOTICONS, config.emoticons.joinToString("\n"))
            .putString(KEY_CUSTOM_REPLACES, encodeReplaces(config.customReplaces))
            .apply()
    }

    private fun parseList(sp: android.content.SharedPreferences, key: String, legacyKey: String, default: List<String>): List<String> {
        val raw = sp.getString(key, null)
        if (raw != null) {
            return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { default }
        }
        val legacy = sp.getString(legacyKey, null)?.trim()
        return if (legacy.isNullOrEmpty()) default else listOf(legacy)
    }

    private fun parsePick(sp: android.content.SharedPreferences, key: String, default: PickMode): PickMode =
        try {
            sp.getString(key, null)?.let { PickMode.valueOf(it) } ?: default
        } catch (e: Exception) {
            default
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
