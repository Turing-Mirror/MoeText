package com.turingmirror.moetext.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformEngineTest {
    @Test fun pluralRulesAreIndependentOfSingularRules() {
        val config = AppConfig(sentenceSuffixEnabled = false, emoticonEnabled = false,
            woMenToBenmiaoMen = true, niMenToZhurenMen = true, niToZhuren = true)
        assertEquals("本喵们 主人们 本喵 主人", TransformEngine.transform("我们 你们 我 你", config, false))
        assertEquals("我们 你们 本喵 主人", TransformEngine.transform("我们 你们 我 你",
            config.copy(woMenToBenmiaoMen = false, niMenToZhurenMen = false), false))
    }

    @Test fun originalWhitespaceAndLiteralSuffixSurvive() {
        val config = AppConfig(sentenceSuffixEnabled = false, emoticonEnabled = false)
        assertEquals("  喵 😀\n本喵  ", TransformEngine.transform("  喵 😀\n我  ", config, false))
        assertEquals(" \n ", TransformEngine.transform(" \n ", config, false))
    }

    private val classical = AppConfig(
        sentenceSuffixEnabled = true,
        sentenceSuffixes = listOf("也"),
        tailEnabled = true,
        tails = listOf("幸甚至哉"),
        emoticonEnabled = false,
        customReplaces = emptyList()
    )

    @Test
    fun incompleteMessageDoesNotReceiveDecorations() {
        assertEquals(
            "不是",
            TransformEngine.transform(
                "不是",
                classical,
                allowRandomTail = false,
                completeMessage = false
            )
        )
    }

    @Test
    fun completedSentenceReceivesSuffixButNotWholeMessageTailWhileTyping() {
        assertEquals(
            "不是也。",
            TransformEngine.transform(
                "不是。",
                classical,
                allowRandomTail = false,
                completeMessage = false
            )
        )
    }

    @Test
    fun completedMessageReceivesSuffixAndWholeMessageTail() {
        val result = TransformEngine.transform(
            "不是",
            classical,
            allowRandomTail = false,
            completeMessage = true
        )
        assertTrue(result.startsWith("不是也"))
        assertTrue(result.endsWith("幸甚至哉"))
    }

    @Test
    fun spacesAreNotConsumedDuringPartialTransform() {
        val config = classical.copy(
            sentenceSuffixEnabled = false,
            tailEnabled = false
        )
        assertEquals(
            "不是 ",
            TransformEngine.transform(
                "不是 ",
                config,
                allowRandomTail = false,
                completeMessage = false
            )
        )
    }

    private val suffixOnly = AppConfig(sentenceSuffixEnabled = true, sentenceSuffixes = listOf("喵"),
        emoticonEnabled = false)

    @Test
    fun partialSentenceNeverDropsRemainder() {
        fun partial(text: String) = TransformEngine.transform(text, suffixOnly, false, completeMessage = false)
        assertEquals("第一句喵。第二句未完成", partial("第一句。第二句未完成"))
        assertEquals("hello world", partial("hello world"))
        assertEquals("你好喵？后文", partial("你好？后文"))
    }

    @Test
    fun qqFaceCodesAreNeverSplit() {
        // U+0014 plus a face id; ids such as ',', '?' and ' ' look like punctuation or spaces.
        assertEquals("好\u0014,喵", TransformEngine.transform("好\u0014,", suffixOnly, false))
        assertEquals("你好\u0014 喵", TransformEngine.transform("你好\u0014 ", suffixOnly, false))
        assertEquals("嗯喵，\u0014?喵", TransformEngine.transform("嗯，\u0014?", suffixOnly, false))
        val question = suffixOnly.copy(sentenceSuffixEnabled = false,
            customReplaces = listOf(CustomReplace(true, "?", "？")))
        assertEquals("吗？\u0014?", TransformEngine.transform("吗?\u0014?", question, false))
    }

    @Test
    fun pictographicTextHasNoLettersOutsideFaces() {
        assertTrue(TextUnits.isPictographic("😀"))
        assertTrue(TextUnits.isPictographic("\u0014A"))
        assertFalse(TextUnits.isPictographic("@张三"))
    }
}
