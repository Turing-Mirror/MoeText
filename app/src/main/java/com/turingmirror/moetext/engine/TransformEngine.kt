package com.turingmirror.moetext.engine

object TransformEngine {

    fun transform(
        original: String,
        config: AppConfig,
        allowRandomTail: Boolean,
        seqIndex: Int = 0
    ): String {
        if (original.isBlank()) return original
        var text = original.trim()

        if (config.woMenToBenmiaoMen) text = ReplaceRule("我们", "本喵们").transform(text)
        if (config.niMenToZhurenMen) text = ReplaceRule("你们", "主人们").transform(text)
        if (config.woToBenmiao) text = ReplaceRule("我", "本喵").transform(text)
        if (config.niToZhuren) text = ReplaceRule("你", "主人").transform(text)
        for (r in config.customReplaces) {
            if (r.enabled && r.from.isNotEmpty()) text = ReplaceRule(r.from, r.to).transform(text)
        }
        if (config.sentenceSuffixEnabled) {
            text = SentenceSuffixRule(config.sentenceSuffixes, config.sentenceSuffixPick, seqIndex).transform(text)
        }
        if (config.tailEnabled) {
            text = TailRule(config.tails, config.tailPick, seqIndex).transform(text)
        }
        if (config.emoticonEnabled && allowRandomTail) {
            text = RandomTailRule(config.emoticons.filter { it.isNotBlank() }).transform(text)
        }
        return text
    }
}
