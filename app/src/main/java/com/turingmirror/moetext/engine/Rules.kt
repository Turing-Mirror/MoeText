package com.turingmirror.moetext.engine

import java.util.Random

interface TransformRule {
    fun transform(input: String): String
}

data class CustomReplace(
    val enabled: Boolean = true,
    val from: String = "",
    val to: String = ""
)

enum class PickMode { SEQUENTIAL, RANDOM }

data class AppConfig(
    val realtimeMode: Boolean = false,
    val woToBenmiao: Boolean = true,
    val niToZhuren: Boolean = false,
    val woMenToBenmiaoMen: Boolean = false,
    val niMenToZhurenMen: Boolean = false,
    val sentenceSuffixEnabled: Boolean = true,
    val sentenceSuffixes: List<String> = listOf("喵"),
    val sentenceSuffixPick: PickMode = PickMode.SEQUENTIAL,
    val tailEnabled: Boolean = false,
    val tails: List<String> = emptyList(),
    val tailPick: PickMode = PickMode.SEQUENTIAL,
    val emoticonEnabled: Boolean = true,
    val emoticons: List<String> = BUILTIN_EMOTICONS,
    val customReplaces: List<CustomReplace> = emptyList()
) {
    companion object {
        val BUILTIN_EMOTICONS = listOf(
            "^⌯𖥦⌯^ ੭ ^", "⌯'ㅅ'⌯", "=^𖥦^=", "⌯•ㅅ•⌯", "ฅ•̀∀•́ฅ",
            "ฅ ̳͒•ˑ̫• ̳͒ฅ♡", "ฅ(̳•·̫•̳ฅ)♡", "ฅ^••^ฅ", "=^•ω•^=", "₍^ >ヮ<^₎",
            "/ᐠ - ˕ -マ Ⳋ", "ฅ^•ﻌ•^ฅ", "ฅ՞•ﻌ•՞ฅ", "(ฅ´ω`ฅ)", "ฅ(*`ω´*)ฅ",
            "ฅ꒰ ⸝˶• •˶⸝꒱ฅ", "₍˄·͈༝·͈˄*₎◞ ̑̑", "!!^⌯𖥦⌯^ ੭!!", "₍^⸝⸝> ·̫ <⸝⸝ ^₎", "ฅ^._.^ฅ",
            "₍🎀˄•͈༝•͈˄₎ฅ˒˒", "^•͈༝•^ฅ", "꒰ఎ(^ . ֑ .^)໒꒱", "ฅ●ω●ฅ", "₍⸍⸌·͈༝·͈⸍⸌₎◞",
            "(>^ω^<)", "ฅ^-﹃-^ฅ", "^ ̳ට ̫ ට ̳^", "୧₍˄·͈༝·͈˄₎୨", "^ ̳ᴗ  ̫ ᴗ ̳^",
            "˓˓ก(⸍⸌̣ʷ̣̫⸍̣⸌₎ค˒˒", "ヽ(ฅ≧へ≦)ฅ", "(`･ω･´)ฅ", "(=^･ᴥ･^=)", "(^ω^ฅ)",
            "ฅ(≧▽≦)ฅ", "ฅ(=´▽`=)ฅ", "ヾ((๑˘ㅂ˘๑)ฅ", "(ฅ◑ω◑ฅ)", "(๑•̀ω•́ฅ)",
            "(ฅ>ω<*ฅ)", "(=^.^=)", "(=´ᴥ`)", "(=ↀωↀ=)", "(=^-ω-^=)",
            "ฅ(*°ω°*ฅ)", "ヽ(=^･ω･^=)丿", "(^•ᴥ•^)", "( Φ ω Φ )", "(=^x^=)",
            "ฅ( ̳• ◡ • ̳)ฅ", "o( =•ω•= )m", "~o( =∩ω∩= )m", "≡ω≡"
        )
    }
}

class ReplaceRule(private val from: String, private val to: String) : TransformRule {
    override fun transform(input: String): String =
        if (from.isEmpty()) input else input.replace(from, to)
}

class SentenceSuffixRule(
    private val candidates: List<String>,
    private val pickMode: PickMode,
    private val seqIndex: Int,
    private val includeTrailingSegment: Boolean = true
) : TransformRule {

    override fun transform(input: String): String {
        val suffix = pickFrom(candidates, pickMode, seqIndex)
        if (suffix.isEmpty()) return input
        return applySuffix(input, suffix)
    }

    private fun applySuffix(input: String, suffix: String): String {
        if (suffix.isEmpty()) return input
        val sb = StringBuilder()
        var last = 0
        for (m in separators.findAll(input)) {
            val seg = input.substring(last, m.range.first)
            sb.append(seg)
            if (seg.isNotBlank()) {
                sb.append(suffix)
            }
            sb.append(m.value)
            last = m.range.last + 1
        }
        if (last < input.length) {
            val seg = input.substring(last)
            sb.append(seg)
            if (includeTrailingSegment && seg.isNotBlank()) {
                sb.append(suffix)
            }
        }
        val result = sb.toString()
        return result.ifEmpty { input }
    }

    companion object {
        private val separators = Regex("([，,。！!？?；;：:\\n]+)")
        fun pickFrom(pool: List<String>, pickMode: PickMode, seqIndex: Int): String {
            val cleaned = pool.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) return ""
            return when (pickMode) {
                PickMode.SEQUENTIAL -> cleaned[Math.floorMod(seqIndex, cleaned.size)]
                PickMode.RANDOM -> cleaned[RNG.nextInt(cleaned.size)]
            }
        }

        private val RNG = Random()
    }
}

class TailRule(
    private val candidates: List<String>,
    private val pickMode: PickMode,
    private val seqIndex: Int
) : TransformRule {
    override fun transform(input: String): String {
        val picked = SentenceSuffixRule.pickFrom(candidates, pickMode, seqIndex)
        return if (picked.isEmpty()) input else "$input $picked"
    }
}

class RandomTailRule(private val pool: List<String>) : TransformRule {
    override fun transform(input: String): String {
        if (pool.isEmpty()) return input
        val pick = pool[RNG.nextInt(pool.size)]
        return if (pick.isEmpty()) input else "$input $pick"
    }

    companion object {
        private val RNG = Random()
    }
}
