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

data class AppConfig(
    val realtimeMode: Boolean = false,
    val woToBenmiao: Boolean = true,
    val niToZhuren: Boolean = false,
    val sentenceSuffixEnabled: Boolean = true,
    val sentenceSuffixText: String = "喵",
    val tailEnabled: Boolean = false,
    val tailText: String = "",
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

class SentenceSuffixRule(private val suffix: String) : TransformRule {
    override fun transform(input: String): String {
        if (suffix.isEmpty()) return input
        val regex = Regex("([，,。！!？?\\s]+)")
        val sb = StringBuilder()
        var last = 0
        for (m in regex.findAll(input)) {
            val seg = input.substring(last, m.range.first).trim()
            if (seg.isNotEmpty()) {
                sb.append(seg)
                sb.append(suffix)
            }
            sb.append(m.value)
            last = m.range.last + 1
        }
        if (last < input.length) {
            val seg = input.substring(last).trim()
            if (seg.isNotEmpty()) {
                sb.append(seg)
                sb.append(suffix)
            }
        }
        val result = sb.toString().trim()
        return result.ifEmpty { input + suffix }
    }
}

class TailRule(private val text: String) : TransformRule {
    override fun transform(input: String): String =
        if (text.isEmpty()) input else "$input $text"
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
