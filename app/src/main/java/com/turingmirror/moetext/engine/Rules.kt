package com.turingmirror.moetext.engine

import java.util.Random

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

/** Picks one entry of a library; [index] drives sequential rotation. */
object Picker {
    private val rng = Random()

    fun pick(pool: List<String>, mode: PickMode, index: Int): String {
        val cleaned = pool.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return ""
        return when (mode) {
            PickMode.SEQUENTIAL -> cleaned[Math.floorMod(index, cleaned.size)]
            PickMode.RANDOM -> cleaned[rng.nextInt(cleaned.size)]
        }
    }
}
