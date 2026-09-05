package com.turingmirror.moetext.engine

object TransformEngine {
    private val pronouns = Regex("我们|你们|我|你")

    fun transform(
        original: String,
        config: AppConfig,
        allowRandomTail: Boolean,
        seqIndex: Int = 0,
        completeMessage: Boolean = true
    ): String {
        if (original.isBlank()) return original
        val leading = original.takeWhile { it.isWhitespace() }
        val trailing = original.takeLastWhile { it.isWhitespace() }
        var text = original.trim()

        text = pronouns.replace(text) { match ->
            when (match.value) {
                "我们" -> if (config.woMenToBenmiaoMen) "本喵们" else match.value
                "你们" -> if (config.niMenToZhurenMen) "主人们" else match.value
                "我" -> if (config.woToBenmiao) "本喵" else match.value
                else -> if (config.niToZhuren) "主人" else match.value
            }
        }
        for (r in config.customReplaces) {
            if (r.enabled && r.from.isNotEmpty()) text = ReplaceRule(r.from, r.to).transform(text)
        }
        if (config.sentenceSuffixEnabled) {
            text = SentenceSuffixRule(
                config.sentenceSuffixes,
                config.sentenceSuffixPick,
                seqIndex,
                includeTrailingSegment = completeMessage
            ).transform(text)
        }
        if (completeMessage && config.tailEnabled) {
            text = TailRule(config.tails, config.tailPick, seqIndex).transform(text)
        }
        if (completeMessage && config.emoticonEnabled && allowRandomTail) {
            text = RandomTailRule(config.emoticons.filter { it.isNotBlank() }).transform(text)
        }
        return leading + text + trailing
    }
}
