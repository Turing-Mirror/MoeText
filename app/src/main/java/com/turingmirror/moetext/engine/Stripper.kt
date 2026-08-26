package com.turingmirror.moetext.engine

object Stripper {

    private val DECOR_RUN = Regex("\\s*[\\p{S}\\p{So}\\p{Sm}\\p{Sk}\\p{P}]{3,}\\s*")

    fun strip(text: String, config: AppConfig): String {
        if (text.isEmpty()) return text
        var work = text

        val tails = buildList {
            if (config.tailEnabled && config.tailText.isNotBlank()) add(config.tailText.trim())
            addAll(config.emoticons.filter { it.isNotBlank() })
        }.sortedByDescending { it.length }

        for (t in tails) {
            work = work.replace(" $t", "").replace(t, "")
        }

        val protections = mutableListOf<String>()
        if (config.woToBenmiao) protections.add("本喵")
        if (config.niToZhuren) protections.add("主人")
        for (r in config.customReplaces) {
            if (r.enabled && r.to.isNotEmpty()) protections.add(r.to)
        }
        val distinct = protections.distinct()
        for ((i, p) in distinct.withIndex()) {
            work = work.replace(p, "\uE000$i\uE000")
        }

        if (config.sentenceSuffixEnabled && config.sentenceSuffixText.isNotEmpty()) {
            work = work.replace(config.sentenceSuffixText, "")
        }

        work = work.replace(DECOR_RUN, " ")

        for ((i, p) in distinct.withIndex()) {
            work = work.replace("\uE000$i\uE000", p)
        }
        return work.trim()
    }
}
