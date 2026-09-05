package com.turingmirror.moetext.data

import java.io.Reader

/** Reads at most [limit] UTF-16 units and rejects larger input instead of truncating it. */
fun Reader.readLimitedText(limit: Int): String {
    val buffer = CharArray(limit + 1)
    var size = 0
    while (size < buffer.size) {
        val count = read(buffer, size, buffer.size - size)
        if (count < 0) break
        size += count
    }
    require(size <= limit) { "Text exceeds size limit" }
    return String(buffer, 0, size)
}
