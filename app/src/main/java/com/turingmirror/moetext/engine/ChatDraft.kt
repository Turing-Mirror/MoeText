package com.turingmirror.moetext.engine

/** This editor's provenance; no reverse dictionary or global decoration stripping. */
class ChatDraft {
    private var original = ""
    private var frozen = BooleanArray(0)
    private var surface = MappedText.original("")
    private var choicesFor: AppConfig? = null
    private var choices: AppConfig? = null
    private var suppressDecorations = false
    private var sequence = 0
    private var pending: MappedText? = null
    private var accepted: MappedText? = null

    data class Result(val text: String, val selectionStart: Int, val selectionEnd: Int)

    fun reset() {
        if (original.isNotEmpty()) sequence++
        original = ""
        frozen = BooleanArray(0)
        surface = MappedText.original("")
        choicesFor = null
        choices = null
        suppressDecorations = false
        pending = null
        accepted = null
    }

    /** Observe IME edits even when rendering waits for composition to finish. */
    fun observe(text: String) {
        if (text.isEmpty()) { reset(); return }
        if (text == surface.text) return
        accepted?.let {
            surface = it
            accepted = null
            if (it.text == text) return
        }
        accepted = null
        if (surface.text.isEmpty()) {
            original = text
            frozen = BooleanArray(text.length)
            surface = MappedText.original(text)
            return
        }
        val previous = surface.text
        var begin = 0
        while (begin < minOf(previous.length, text.length) && previous[begin] == text[begin]) begin++
        if (begin > 0 && begin < previous.length && previous[begin].isLowSurrogate()) begin--
        var oldEnd = previous.length
        var newEnd = text.length
        while (oldEnd > begin && newEnd > begin && previous[oldEnd - 1] == text[newEnd - 1]) {
            oldEnd--; newEnd--
        }
        if (oldEnd < previous.length && oldEnd > begin && previous[oldEnd].isLowSurrogate()) {
            oldEnd++; newEnd++
        }
        var left = begin
        var right = oldEnd
        fun sameSpan(a: Int, b: Int): Boolean = surface.cells[a].let { x ->
            surface.cells[b].let { y -> x.start == y.start && x.end == y.end }
        }
        while (left > 0 && left < previous.length && sameSpan(left - 1, left)) left--
        while (right > 0 && right < previous.length && sameSpan(right - 1, right)) right++
        val from = surface.sourceOffset(left)
        val to = if (right > left) surface.cells[right - 1].end else from
        val replacement = text.substring(left, newEnd + right - oldEnd)
        val editedCells = surface.cells.subList(left, right)
        val literalEdit = left != begin || right != oldEnd
        if (editedCells.any { it.start == it.end }) suppressDecorations = true
        val shift = replacement.length - (to - from)
        original = original.replaceRange(from, to, replacement)
        frozen = frozen.take(from).toBooleanArray() + BooleanArray(replacement.length) { literalEdit } +
            frozen.drop(to).toBooleanArray()
        val cells = ArrayList<TextCell>()
        cells.addAll(surface.cells.take(left))
        replacement.forEachIndexed { i, c -> cells.add(TextCell(c, from + i, from + i + 1)) }
        cells.addAll(surface.cells.drop(right).map { it.copy(start = it.start + shift, end = it.end + shift) })
        surface = MappedText(cells, original.length)
        pending = null
    }

    fun render(config: AppConfig, complete: Boolean, selectionStart: Int, selectionEnd: Int): Result {
        if (choicesFor != config) {
            fun pick(pool: List<String>, mode: PickMode): List<String> =
                listOf(SentenceSuffixRule.pickFrom(pool, mode, sequence))
            choices = config.copy(
                sentenceSuffixes = pick(config.sentenceSuffixes, config.sentenceSuffixPick),
                sentenceSuffixPick = PickMode.SEQUENTIAL,
                tails = pick(config.tails, config.tailPick), tailPick = PickMode.SEQUENTIAL,
                emoticons = config.emoticons.filter { it.isNotBlank() }.randomOrNull()?.let { listOf(it) } ?: emptyList()
            )
            choicesFor = config
        }
        val selected = choices!!
        val active = if (suppressDecorations) selected.copy(sentenceSuffixEnabled = false,
            tailEnabled = false, emoticonEnabled = false) else selected
        val next = TransformEngine.render(original, active, true, 0, complete, frozen)
        pending = next
        fun selection(offset: Int) = if (offset < 0) -1 else next.displayOffset(surface.sourceOffset(offset))
        return Result(next.text, selection(selectionStart), selection(selectionEnd))
    }

    /** Accepted actions can become visible on a later accessibility snapshot. */
    fun written() { accepted = pending; pending = null }
}
