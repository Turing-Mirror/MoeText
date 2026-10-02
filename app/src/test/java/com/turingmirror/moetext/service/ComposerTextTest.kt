package com.turingmirror.moetext.service

import org.junit.Assert.*
import org.junit.Test

class ComposerTextTest {
    @Test fun textEventPayloadGivesTheNewText() {
        assertEquals("你好", ComposerText.afterChange(listOf("你好"), "你", 0, 1))
    }

    @Test fun emptiedEditorReportsItsHintAsEventText() {
        assertEquals("", ComposerText.afterChange(listOf("发消息"), "好", 1, 0))
    }

    @Test fun inconsistentPayloadIsUnknown() {
        assertNull(ComposerText.afterChange(listOf("你好"), "你", 0, 3))
        assertNull(ComposerText.afterChange(emptyList(), null, -1, -1))
        assertEquals("你好", ComposerText.afterChange(listOf("你好"), null, -1, -1))
    }

    @Test fun diagnosticRepeatsCollapse() {
        val log = DiagLog(3)
        repeat(3) { log.add("retry: changed") }
        log.add("write: verified")
        log.add("a")
        log.add("b")
        assertEquals(listOf("write: verified", "a", "b"), log.snapshot())
        val fresh = DiagLog(3)
        repeat(3) { fresh.add("retry: changed") }
        assertEquals(listOf("retry: changed ×3"), fresh.snapshot())
    }
}
