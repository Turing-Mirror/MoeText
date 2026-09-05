package com.turingmirror.moetext.engine

/** UTF-16 offsets match Android editor selections and accessibility events. */
internal data class TextCell(val value: Char, val start: Int, val end: Int)

internal class MappedText(val cells: List<TextCell>, val sourceLength: Int) {
    val text: String = buildString(cells.size) { cells.forEach { append(it.value) } }

    fun sourceOffset(offset: Int): Int = when {
        offset <= 0 -> cells.firstOrNull()?.start ?: 0
        offset >= cells.size -> sourceLength
        else -> cells[offset].start
    }

    fun displayOffset(offset: Int): Int =
        // Keep the caret before generated suffixes for continued typing.
        cells.indexOfFirst { it.start >= offset }.takeIf { it >= 0 } ?: cells.size

    fun replace(matches: List<Pair<IntRange, String>>, frozen: BooleanArray): MappedText {
        if (matches.isEmpty()) return this
        // Repeated user replacement rules can otherwise grow text exponentially.
        val size = matches.fold(cells.size.toLong()) { total, (range, replacement) ->
            total + replacement.length - (range.last + 1 - range.first)
        }
        if (size > 65536) return this
        val result = ArrayList<TextCell>(cells.size)
        var cursor = 0
        for ((range, replacement) in matches) {
            val begin = range.first
            val end = range.last + 1
            val from = if (begin < cells.size) cells[begin].start else sourceLength
            val to = if (end > begin) cells[end - 1].end else from
            if ((from until to).any { frozen.getOrElse(it) { false } }) continue
            result.addAll(cells.subList(cursor, begin))
            if (text.substring(begin, end) == replacement) result.addAll(cells.subList(begin, end))
            else replacement.forEach { result.add(TextCell(it, from, to)) }
            cursor = end
        }
        result.addAll(cells.subList(cursor, cells.size))
        return MappedText(result, sourceLength)
    }

    companion object {
        fun original(text: String) = MappedText(text.mapIndexed { i, c -> TextCell(c, i, i + 1) }, text.length)
    }
}
