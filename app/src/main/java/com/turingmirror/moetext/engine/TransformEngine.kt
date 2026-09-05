package com.turingmirror.moetext.engine

object TransformEngine {
    private val pronouns = Regex("我们|你们|我|你")
    private val separators = Regex("[，,。！!？?；;：:\\r\\n]+")

    fun transform(
        original: String,
        config: AppConfig,
        allowRandomTail: Boolean,
        seqIndex: Int = 0,
        completeMessage: Boolean = true
    ): String = render(original, config, allowRandomTail, seqIndex, completeMessage).text

    internal fun render(original: String, config: AppConfig, allowRandomTail: Boolean,
        seqIndex: Int = 0, completeMessage: Boolean = true,
        frozen: BooleanArray = BooleanArray(original.length)): MappedText {
        var mapped = MappedText.original(original)
        if (original.isBlank()) return mapped
        mapped = mapped.replace(pronouns.findAll(mapped.text).map { match ->
            match.range to when (match.value) {
                "我们" -> if (config.woMenToBenmiaoMen) "本喵们" else match.value
                "你们" -> if (config.niMenToZhurenMen) "主人们" else match.value
                "我" -> if (config.woToBenmiao) "本喵" else match.value
                else -> if (config.niToZhuren) "主人" else match.value
            }
        }.toList(), frozen)
        for (rule in config.customReplaces) {
            if (!rule.enabled || rule.from.isEmpty()) continue
            val edits = ArrayList<Pair<IntRange, String>>()
            var start = mapped.text.indexOf(rule.from)
            while (start >= 0) {
                edits.add((start until start + rule.from.length) to rule.to)
                start = mapped.text.indexOf(rule.from, start + rule.from.length)
            }
            mapped = mapped.replace(edits, frozen)
        }
        if (config.sentenceSuffixEnabled) {
            val suffix = SentenceSuffixRule.pickFrom(config.sentenceSuffixes, config.sentenceSuffixPick, seqIndex)
            val edits = ArrayList<Pair<IntRange, String>>()
            var start = 0
            for (match in separators.findAll(mapped.text)) {
                if (mapped.text.substring(start, match.range.first).isNotBlank()) {
                    edits.add((match.range.first until match.range.first) to suffix)
                }
                start = match.range.last + 1
            }
            val end = mapped.text.trimEnd().length
            if (completeMessage && start < end && mapped.text.substring(start, end).isNotBlank()) {
                edits.add((end until end) to suffix)
            }
            mapped = mapped.replace(edits, frozen)
        }
        if (completeMessage) {
            val tail = buildString {
                if (config.tailEnabled) {
                    val picked = SentenceSuffixRule.pickFrom(config.tails, config.tailPick, seqIndex)
                    if (picked.isNotEmpty()) append(" $picked")
                }
                if (config.emoticonEnabled && allowRandomTail) {
                    val picked = config.emoticons.filter { it.isNotBlank() }.randomOrNull()
                    if (picked != null) append(" $picked")
                }
            }
            val end = mapped.text.trimEnd().length
            mapped = mapped.replace(listOf((end until end) to tail), frozen)
        }
        return mapped
    }
}
