package com.turingmirror.moetext.engine

import org.junit.Assert.assertEquals
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
}
