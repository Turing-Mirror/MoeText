package com.turingmirror.moetext.engine

import org.junit.Assert.*
import org.junit.Test

class BidiToolsTest {
    @Test fun nicknameMatchesExample() {
        assertEquals("千早爱音 \u2067\u202D\u2067～喵\u2067\u202D\u00A0", BidiTools.nickname("千早爱音", "喵～"))
    }
    @Test fun cleansControlsWithoutDeletingTextOrEmoji() {
        assertEquals("喵～😀", BidiTools.clean("\u2067喵～😀\u202D"))
    }
    @Test fun isolateTerminatorAlsoClosesInnerEmbeddings() {
        assertEquals("", BidiTools.closure("\u2067\u202Dabc\u2069"))
        assertEquals("\u202C\u2069", BidiTools.closure("\u2067\u202Dabc"))
    }
    @Test fun paragraphsResetAndProtectionPreservesBody() {
        assertEquals("", BidiTools.closure("\u2067abc\n"))
        assertEquals("甲\n乙😀", BidiTools.clean(BidiTools.protect("\u2067", "甲\n乙😀")))
    }
    @Test fun partialSentenceNeverDropsRemainder() {
        assertEquals("第一句喵。第二句未完成", SentenceSuffixRule(listOf("喵"), PickMode.SEQUENTIAL, 0, false).transform("第一句。第二句未完成"))
        assertEquals("hello world", SentenceSuffixRule(listOf("喵"), PickMode.SEQUENTIAL, 0, false).transform("hello world"))
        assertEquals("你好喵？后文", SentenceSuffixRule(listOf("喵"), PickMode.SEQUENTIAL, 0, false).transform("你好？后文"))
    }
    @Test fun reversalPreservesSurrogatePairs() {
        assertEquals("A \u2067\u202D\u2067～😀\u2067\u202D\u00A0", BidiTools.nickname("A", "😀～"))
    }
    @Test fun strongerProtectionPreservesMixedTextAndParagraphs() {
        val body = "中文 abc العربية 123\r\n第二段\u2029尾段"
        val result = BidiTools.protect("\u2067\u202D".repeat(200), body, true)
        assertEquals(body, BidiTools.clean(result))
        assertEquals("", BidiTools.closure(result))
        assertEquals(BidiTools.clean(result), BidiTools.clean(BidiTools.clean(result)))
    }
    @Test fun inspectShowsInvisibleControls() {
        assertEquals("[RLI][LRO]喵[PDI]", BidiTools.inspect("\u2067\u202D喵\u2069"))
    }
}
