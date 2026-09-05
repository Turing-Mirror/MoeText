package com.turingmirror.moetext.engine

import com.turingmirror.moetext.data.readLimitedText
import org.junit.Assert.assertEquals
import org.junit.Test

class TextIOTest {
    @Test fun acceptsExactLimit() {
        assertEquals("你好", "你好".reader().readLimitedText(2))
        assertEquals("", "".reader().readLimitedText(2))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedInput() {
        "你好啊".reader().readLimitedText(2)
    }
}
