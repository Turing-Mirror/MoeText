package com.turingmirror.moetext.engine

import com.turingmirror.moetext.service.ChatTargets
import org.junit.Assert.*
import org.junit.Test

class ChatDraftTest {
    private val plain = AppConfig(sentenceSuffixEnabled = false, emoticonEnabled = false)

    private fun update(draft: ChatDraft, text: String, config: AppConfig = plain,
        complete: Boolean = true, start: Int = text.length, end: Int = start): ChatDraft.Result {
        draft.observe(text)
        val result = draft.render(config, complete, start, end)
        draft.written()
        draft.observe(result.text)
        return result
    }

    @Test fun appendAfterReplacementKeepsOriginalPlural() {
        val draft = ChatDraft()
        assertEquals("本喵", update(draft, "我").text)
        assertEquals("我们", update(draft, "本喵们").text)
    }

    @Test fun middleEditsKeepUnaffectedSourceAndContinueTransforming() {
        val draft = ChatDraft()
        assertEquals("本喵喜欢你", update(draft, "我喜欢你").text)
        val result = update(draft, "本喵很喜欢你", start = 3)
        assertEquals("本喵很喜欢你", result.text)
        assertEquals(3, result.selectionStart)
        assertEquals("本喵很喜欢你，本喵也是", update(draft, "本喵很喜欢你，我也是").text)
    }

    @Test fun editingExpandedReplacementDoesNotRepeatIt() {
        val config = plain.copy(customReplaces = listOf(CustomReplace(true, "谢谢", "谢谢啦")))
        val draft = ChatDraft()
        assertEquals("谢谢啦", update(draft, "谢谢", config).text)
        assertEquals("谢谢", update(draft, "谢谢", config).text)
        assertEquals("谢谢 本喵", update(draft, "谢谢 我", config).text)
    }

    @Test fun suffixMovesAfterContinuedTypingAndCaretStaysBeforeIt() {
        val config = plain.copy(sentenceSuffixEnabled = true)
        val draft = ChatDraft()
        val first = update(draft, "我", config)
        assertEquals("本喵喵", first.text)
        assertEquals(2, first.selectionStart)
        val next = update(draft, "本喵好喵", config, start = 3)
        assertEquals("本喵好喵", next.text)
        assertEquals(3, next.selectionStart)
    }

    @Test fun manualDecorationRemovalIsRespectedWithoutDisablingReplacements() {
        val config = plain.copy(sentenceSuffixEnabled = true)
        val draft = ChatDraft()
        assertEquals("你好喵", update(draft, "你好", config).text)
        assertEquals("你好", update(draft, "你好", config).text)
        assertEquals("你好本喵", update(draft, "你好我", config).text)
    }

    @Test fun failedWriteCanRetryWithoutDuplicatingInput() {
        val draft = ChatDraft()
        update(draft, "我")
        draft.observe("本喵好")
        val first = draft.render(plain, true, 3, 3)
        draft.observe("本喵好")
        assertEquals(first, draft.render(plain, true, 3, 3))
    }

    @Test fun delayedEchoAndNextInputUseAcceptedMapping() {
        val draft = ChatDraft()
        draft.observe("我")
        assertEquals("本喵", draft.render(plain, true, 1, 1).text)
        draft.written()
        draft.observe("我") // stale snapshot
        draft.observe("本喵们") // echo coalesced with the next keystroke
        assertEquals("我们", draft.render(plain, true, 3, 3).text)
    }

    @Test fun replacingWholeConvertedWordTransformsNewInput() {
        val draft = ChatDraft()
        val config = plain.copy(niToZhuren = true)
        update(draft, "我", config)
        assertEquals("主人", update(draft, "你", config).text)
    }

    @Test fun insertingInsideExpandedWordPreservesTheManualEdit() {
        val draft = ChatDraft()
        update(draft, "我")
        assertEquals("本小喵", update(draft, "本小喵", start = 2).text)
        assertEquals("本小喵和本喵", update(draft, "本小喵和我").text)
    }

    @Test fun deletingRawTextAcrossSeveralConvertedWordsKeepsTheRest() {
        val draft = ChatDraft()
        val config = plain.copy(niToZhuren = true)
        update(draft, "我喜欢你", config)
        assertEquals("本喵主人", update(draft, "本喵主人", config, start = 2).text)
    }

    @Test fun compositionUpdatesCanBeObservedWithoutWriting() {
        val draft = ChatDraft()
        update(draft, "我")
        draft.observe("本喵n")
        draft.observe("本喵ni")
        draft.observe("本喵你")
        assertEquals("本喵主人", draft.render(plain.copy(niToZhuren = true), true, 3, 3).text)
    }

    @Test fun randomChoicesStayStableAcrossEditsAndCompletion() {
        val config = plain.copy(sentenceSuffixEnabled = true, sentenceSuffixPick = PickMode.RANDOM,
            sentenceSuffixes = listOf("甲", "乙", "丙"), emoticonEnabled = true, emoticons = listOf("A", "B", "C"))
        val draft = ChatDraft()
        val first = update(draft, "好", config)
        val decoration = first.text.removePrefix("好")
        repeat(20) { i ->
            assertEquals("好$i$decoration", update(draft, "好$i$decoration", config, start = 2).text)
        }
    }

    @Test fun clearAdvancesSequentialSelectionAndResetsLiteralEdits() {
        val config = plain.copy(sentenceSuffixEnabled = true, sentenceSuffixes = listOf("甲", "乙"))
        val draft = ChatDraft()
        assertEquals("好甲", update(draft, "好", config).text)
        update(draft, "", config)
        assertEquals("好乙", update(draft, "好", config).text)
    }

    @Test fun emojiAndSelectionsSurviveMiddleReplacement() {
        val draft = ChatDraft()
        val result = update(draft, "😀我好", start = 2, end = 3)
        assertEquals("😀本喵好", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(4, result.selectionEnd)
        assertEquals("😁本喵好", update(draft, "😁本喵好").text)
    }

    @Test fun discordComposerRecognitionExcludesSearchAndOtherApps() {
        assertTrue(ChatTargets.matches("com.discord", null, "Message #general"))
        assertTrue(ChatTargets.matches("com.discord", null, "发送消息给 @用户"))
        assertFalse(ChatTargets.matches("com.discord", null, "Search messages"))
        assertFalse(ChatTargets.matches("com.discord", null, "修改昵称"))
        assertFalse(ChatTargets.matches("com.example", null, "Message #general"))
        assertFalse(ChatTargets.matches("com.tencent.mobileqq", "com.tencent.mobileqq:id/search", "Message"))
    }

    @Test fun arbitraryEditsWithoutRulesRemainExactlyWhatTheUserTyped() {
        val config = plain.copy(woToBenmiao = false)
        val draft = ChatDraft()
        val random = kotlin.random.Random(27)
        var value = ""
        repeat(300) {
            val start = random.nextInt(value.length + 1)
            val end = random.nextInt(start, value.length + 1)
            val inserted = listOf("", "我", "你", "\n", "hello", "喵～")[random.nextInt(6)]
            value = value.replaceRange(start, end, inserted)
            assertEquals(value, update(draft, value, config, start = start + inserted.length).text)
        }
    }

    @Test fun recursivelyExpandingCustomRulesStayBounded() {
        val config = plain.copy(customReplaces = List(30) { CustomReplace(true, "a", "aa") })
        val result = TransformEngine.transform("a", config, false)
        assertTrue(result.length <= 65536)
        assertTrue(result.all { it == 'a' })
    }
}
