package com.turingmirror.moetext.engine

object Stripper {

    private val DECOR_RUN = Regex("\\s*[\\p{S}\\p{So}\\p{Sm}\\p{Sk}\\p{P}]{3,}\\s*")

    fun strip(text: String, config: AppConfig): String {
        if (text.isEmpty()) return text
        var work = text

        val tails = buildList {
            if (config.tailEnabled) addAll(config.tails.map { it.trim() }.filter { it.isNotEmpty() })
            addAll(config.emoticons.filter { it.isNotBlank() })
        }.sortedByDescending { it.length }

        for (t in tails) {
            work = work.replace(" $t", "").replace(t, "")
        }

        val protections = mutableListOf<String>()
        if (config.woMenToBenmiaoMen) protections.add("本喵们")
        if (config.niMenToZhurenMen) protections.add("主人们")
        if (config.woToBenmiao) protections.add("本喵")
        if (config.niToZhuren) protections.add("主人")
        for (r in config.customReplaces) {
            if (r.enabled && r.to.isNotEmpty()) protections.add(r.to)
        }
        val distinct = protections.distinct()
        for ((i, p) in distinct.withIndex()) {
            work = work.replace(p, "\uE000$i\uE000")
        }

        if (config.sentenceSuffixEnabled) {
            val suffixPool = config.sentenceSuffixes.map { it.trim() }.filter { it.isNotEmpty() }
            if (suffixPool.size == 1) {
                work = work.replace(suffixPool[0], "")
            } else {
                for (s in suffixPool) work = work.replace(s, "")
            }
        }

        work = work.replace(DECOR_RUN, " ")

        for ((i, p) in distinct.withIndex()) {
            work = work.replace("\uE000$i\uE000", p)
        }
        return work.trim()
    }
}
