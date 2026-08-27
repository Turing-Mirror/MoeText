package com.turingmirror.moetext.data

import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.engine.CustomReplace
import com.turingmirror.moetext.engine.PickMode
import org.json.JSONArray
import org.json.JSONObject

data class StylePreset(
    val id: String,
    val name: String,
    val config: AppConfig
)

object StylePresets {

    val BUILTIN = listOf(catgirl(), classical(), emojiRiddle(), oracle())

    fun apply(current: AppConfig, source: AppConfig): AppConfig = current.copy(
        woToBenmiao = source.woToBenmiao,
        niToZhuren = source.niToZhuren,
        woMenToBenmiaoMen = source.woMenToBenmiaoMen,
        niMenToZhurenMen = source.niMenToZhurenMen,
        sentenceSuffixEnabled = source.sentenceSuffixEnabled,
        sentenceSuffixes = source.sentenceSuffixes,
        sentenceSuffixPick = source.sentenceSuffixPick,
        tailEnabled = source.tailEnabled,
        tails = source.tails,
        tailPick = source.tailPick,
        emoticonEnabled = source.emoticonEnabled,
        emoticons = source.emoticons,
        customReplaces = source.customReplaces
    )

    private fun catgirl() = StylePreset(
        "catgirl", "猫娘",
        AppConfig(
            woToBenmiao = true,
            niToZhuren = false,
            woMenToBenmiaoMen = true,
            niMenToZhurenMen = false,
            sentenceSuffixEnabled = true,
            sentenceSuffixes = listOf("喵"),
            sentenceSuffixPick = PickMode.SEQUENTIAL,
            tailEnabled = false,
            tails = emptyList(),
            emoticonEnabled = true,
            emoticons = AppConfig.BUILTIN_EMOTICONS,
            customReplaces = listOf(
                CustomReplace(true, "好的", "好哒"),
                CustomReplace(true, "谢谢", "谢谢啦"),
                CustomReplace(true, "收到", "收到啦"),
                CustomReplace(true, "对不起", "对不起啦"),
                CustomReplace(true, "抱歉", "抱歉啦"),
                CustomReplace(true, "辛苦了", "辛苦啦"),
                CustomReplace(true, "真的吗", "真的嘛"),
                CustomReplace(true, "可以吗", "可以嘛"),
                CustomReplace(true, "好吗", "好不好嘛")
            )
        )
    )

    private fun classical() = StylePreset(
        "classical", "文言",
        AppConfig(
            woToBenmiao = false,
            niToZhuren = false,
            woMenToBenmiaoMen = false,
            niMenToZhurenMen = false,
            sentenceSuffixEnabled = true,
            sentenceSuffixes = listOf("也", "矣", "焉"),
            sentenceSuffixPick = PickMode.SEQUENTIAL,
            tailEnabled = true,
            tails = listOf("幸甚至哉"),
            tailPick = PickMode.SEQUENTIAL,
            emoticonEnabled = false,
            customReplaces = listOf(
                CustomReplace(true, "吃了吗", "饭否"),
                CustomReplace(true, "干嘛呢", "何为耶"),
                CustomReplace(true, "你好吗", "安否"),
                CustomReplace(true, "对不起", "愧甚"),
                CustomReplace(true, "谢谢", "谢过"),
                CustomReplace(true, "太好了", "妙哉"),
                CustomReplace(true, "完蛋", "休矣"),
                CustomReplace(true, "对不对", "然否"),
                CustomReplace(true, "是不是", "莫非"),
                CustomReplace(true, "好不好", "可否"),
                CustomReplace(true, "能不能", "能否"),
                CustomReplace(true, "不知道", "未可知"),
                CustomReplace(true, "有意思", "甚妙"),
                CustomReplace(true, "再见", "就此别过"),
                CustomReplace(true, "为什么", "何故"),
                CustomReplace(true, "怎么样", "何如"),
                CustomReplace(true, "我们现在", "吾侪今"),
                CustomReplace(true, "我们", "吾等"),
                CustomReplace(true, "他们", "彼等"),
                CustomReplace(true, "你们", "尔等"),
                CustomReplace(true, "了吗", "与否"),
                CustomReplace(true, "昨天", "昨日"),
                CustomReplace(true, "今天", "今日"),
                CustomReplace(true, "明天", "明日"),
                CustomReplace(true, "现在", "今"),
                CustomReplace(true, "什么", "何"),
                CustomReplace(true, "怎么", "何以"),
                CustomReplace(true, "但是", "然"),
                CustomReplace(true, "所以", "故"),
                CustomReplace(true, "如果", "若"),
                CustomReplace(true, "因为", "盖因"),
                CustomReplace(true, "于是", "遂"),
                CustomReplace(true, "非常", "甚"),
                CustomReplace(true, "很", "甚"),
                CustomReplace(true, "和", "与"),
                CustomReplace(true, "吗", "乎"),
                CustomReplace(true, "呢", "耶"),
                CustomReplace(true, "啊", "哉"),
                CustomReplace(true, "的", "之"),
                CustomReplace(true, "知道", "知"),
                CustomReplace(true, "明白", "了然"),
                CustomReplace(true, "喜欢", "悦"),
                CustomReplace(true, "开心", "欣欣然"),
                CustomReplace(true, "难过", "戚戚"),
                CustomReplace(true, "生气", "愠怒"),
                CustomReplace(true, "害怕", "惴惴"),
                CustomReplace(true, "讨厌", "嫌恶"),
                CustomReplace(true, "厉害", "善"),
                CustomReplace(true, "无聊", "索然"),
                CustomReplace(true, "疲惫", "倦怠"),
                CustomReplace(true, "冷", "寒"),
                CustomReplace(true, "热", "炎"),
                CustomReplace(true, "快", "疾"),
                CustomReplace(true, "慢", "缓"),
                CustomReplace(true, "多", "众"),
                CustomReplace(true, "少", "寡"),
                CustomReplace(true, "世界", "天下"),
                CustomReplace(true, "时间", "光阴"),
                CustomReplace(true, "朋友", "友人"),
                CustomReplace(true, "老师", "夫子"),
                CustomReplace(true, "学生", "学子"),
                CustomReplace(true, "钱", "金"),
                CustomReplace(true, "医院", "医馆"),
                CustomReplace(true, "回家", "归家"),
                CustomReplace(true, "出发", "启程"),
                CustomReplace(true, "等待", "候"),
                CustomReplace(true, "看", "观"),
                CustomReplace(true, "听", "闻"),
                CustomReplace(true, "想", "思"),
                CustomReplace(true, "说", "曰"),
                CustomReplace(true, "吃饭", "用膳"),
                CustomReplace(true, "手机", "袖中乾坤"),
                CustomReplace(true, "我", "吾"),
                CustomReplace(true, "你", "汝")
            )
        )
    )

    private fun emojiRiddle() = StylePreset(
        "emoji_riddle", "emoji谜语",
        AppConfig(
            woToBenmiao = false,
            niToZhuren = false,
            woMenToBenmiaoMen = false,
            niMenToZhurenMen = false,
            sentenceSuffixEnabled = true,
            sentenceSuffixes = listOf("✨", "🌙", "🔮", "🃏", "🕸️", "🫧"),
            sentenceSuffixPick = PickMode.RANDOM,
            tailEnabled = false,
            tails = emptyList(),
            emoticonEnabled = false,
            customReplaces = listOf(
                CustomReplace(true, "开心", "😊"),
                CustomReplace(true, "高兴", "😆"),
                CustomReplace(true, "生气", "😡"),
                CustomReplace(true, "难过", "😢"),
                CustomReplace(true, "害怕", "😨"),
                CustomReplace(true, "爱", "❤️"),
                CustomReplace(true, "钱", "💰"),
                CustomReplace(true, "吃", "🍚"),
                CustomReplace(true, "喝", "🥤"),
                CustomReplace(true, "睡", "😴"),
                CustomReplace(true, "看", "👀"),
                CustomReplace(true, "听", "👂"),
                CustomReplace(true, "说", "💬"),
                CustomReplace(true, "想", "💭"),
                CustomReplace(true, "走", "🚶"),
                CustomReplace(true, "跑", "🏃"),
                CustomReplace(true, "手机", "📱"),
                CustomReplace(true, "电脑", "💻"),
                CustomReplace(true, "工作", "💼"),
                CustomReplace(true, "学习", "📚"),
                CustomReplace(true, "游戏", "🎮"),
                CustomReplace(true, "朋友", "🫂"),
                CustomReplace(true, "猫", "🐱"),
                CustomReplace(true, "狗", "🐶"),
                CustomReplace(true, "饭", "🍜"),
                CustomReplace(true, "好", "👍"),
                CustomReplace(true, "不行", "🚫"),
                CustomReplace(true, "完成", "✅"),
                CustomReplace(true, "等待", "⏳"),
                CustomReplace(true, "时间", "⏰"),
                CustomReplace(true, "睡觉", "😴"),
                CustomReplace(true, "吃饭", "🍚"),
                CustomReplace(true, "好吃", "😋"),
                CustomReplace(true, "追剧", "📺"),
                CustomReplace(true, "音乐", "🎵"),
                CustomReplace(true, "上学", "🏫"),
                CustomReplace(true, "惊讶", "😲"),
                CustomReplace(true, "无语", "😐"),
                CustomReplace(true, "花", "🌸"),
                CustomReplace(true, "雨", "🌧️"),
                CustomReplace(true, "月亮", "🌙"),
                CustomReplace(true, "星星", "⭐"),
                CustomReplace(true, "加油", "💪"),
                CustomReplace(true, "成功", "🏆"),
                CustomReplace(true, "书", "📖"),
                CustomReplace(true, "茶", "🍵"),
                CustomReplace(true, "咖啡", "☕")
            )
        )
    )

    private fun oracle() = StylePreset(
        "oracle", "神谕",
        AppConfig(
            woToBenmiao = false,
            niToZhuren = false,
            woMenToBenmiaoMen = false,
            niMenToZhurenMen = false,
            sentenceSuffixEnabled = true,
            sentenceSuffixes = listOf("ᚨᛚᛚᚨ", "☽✧⟁", "ᛝᚦᚱ", "⟒⏃⌿"),
            sentenceSuffixPick = PickMode.RANDOM,
            tailEnabled = true,
            tails = listOf("⸸", "✠⟒⏃", "☾⟟⋏"),
            tailPick = PickMode.RANDOM,
            emoticonEnabled = false,
            customReplaces = listOf(
                CustomReplace(true, "秘密", "ᛝ"),
                CustomReplace(true, "时间", "⧖"),
                CustomReplace(true, "世界", "◍"),
                CustomReplace(true, "命运", "⋔"),
                CustomReplace(true, "梦", "☪"),
                CustomReplace(true, "光", "☩"),
                CustomReplace(true, "暗", "☓"),
                CustomReplace(true, "我", "☾"),
                CustomReplace(true, "你", "☿"),
                CustomReplace(true, "他", "♄"),
                CustomReplace(true, "的", "∴"),
                CustomReplace(true, "了", "⟁"),
                CustomReplace(true, "在", "⚓"),
                CustomReplace(true, "是", "☉"),
                CustomReplace(true, "好", "✠"),
                CustomReplace(true, "坏", "⸸"),
                CustomReplace(true, "爱", "♆"),
                CustomReplace(true, "死", "☠"),
                CustomReplace(true, "人", "ᛉ"),
                CustomReplace(true, "希望", "✟"),
                CustomReplace(true, "未来", "⟠"),
                CustomReplace(true, "声音", "⚚"),
                CustomReplace(true, "眼睛", "◉"),
                CustomReplace(true, "心", "ᛊ"),
                CustomReplace(true, "海", "≈"),
                CustomReplace(true, "山", "▲"),
                CustomReplace(true, "星", "✧"),
                CustomReplace(true, "风", "☙"),
                CustomReplace(true, "火", "⌇")
            )
        )
    )
}

object PresetCodec {

    fun toJson(c: AppConfig): String {
        val cr = JSONArray()
        for (r in c.customReplaces) {
            cr.put(JSONObject().put("e", r.enabled).put("f", r.from).put("t", r.to))
        }
        return JSONObject()
            .put("w", c.woToBenmiao)
            .put("n", c.niToZhuren)
            .put("wm", c.woMenToBenmiaoMen)
            .put("nm", c.niMenToZhurenMen)
            .put("se", c.sentenceSuffixEnabled)
            .put("sl", JSONArray(c.sentenceSuffixes))
            .put("sp", c.sentenceSuffixPick.name)
            .put("te", c.tailEnabled)
            .put("tl", JSONArray(c.tails))
            .put("tp", c.tailPick.name)
            .put("ee", c.emoticonEnabled)
            .put("el", JSONArray(c.emoticons))
            .put("cr", cr)
            .toString(2)
    }

    fun parse(text: String): AppConfig? {
        return try {
            val o = JSONObject(text)
            val sl = mutableListOf<String>()
            val slArr = o.optJSONArray("sl")
            if (slArr != null) for (i in 0 until slArr.length()) sl.add(slArr.optString(i))
            val tl = mutableListOf<String>()
            val tlArr = o.optJSONArray("tl")
            if (tlArr != null) for (i in 0 until tlArr.length()) tl.add(tlArr.optString(i))
            val el = mutableListOf<String>()
            val elArr = o.optJSONArray("el")
            if (elArr != null) for (i in 0 until elArr.length()) el.add(elArr.optString(i))
            val cr = mutableListOf<CustomReplace>()
            val crArr = o.optJSONArray("cr")
            if (crArr != null) {
                for (i in 0 until crArr.length()) {
                    val r = crArr.optJSONObject(i) ?: continue
                    cr.add(CustomReplace(r.optBoolean("e", true), r.optString("f"), r.optString("t")))
                }
            }
            AppConfig(
                woToBenmiao = o.optBoolean("w", false),
                niToZhuren = o.optBoolean("n", false),
                woMenToBenmiaoMen = o.optBoolean("wm", false),
                niMenToZhurenMen = o.optBoolean("nm", false),
                sentenceSuffixEnabled = o.optBoolean("se", true),
                sentenceSuffixes = sl.ifEmpty { listOf("喵") },
                sentenceSuffixPick = pick(o.optString("sp", "SEQUENTIAL")),
                tailEnabled = o.optBoolean("te", false),
                tails = tl,
                tailPick = pick(o.optString("tp", "SEQUENTIAL")),
                emoticonEnabled = o.optBoolean("ee", false),
                emoticons = el.ifEmpty { AppConfig.BUILTIN_EMOTICONS },
                customReplaces = cr
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun pick(name: String): PickMode =
        try {
            PickMode.valueOf(name)
        } catch (e: Exception) {
            PickMode.SEQUENTIAL
        }
}
