package com.turingmirror.moetext.engine

/** Explicit characters are escaped in source so code review is not reordered. */
object BidiTools {
    private val names = mapOf(
        '\u061C' to "ALM", '\u200E' to "LRM", '\u200F' to "RLM",
        '\u202A' to "LRE", '\u202B' to "RLE", '\u202C' to "PDF",
        '\u202D' to "LRO", '\u202E' to "RLO", '\u2066' to "LRI",
        '\u2067' to "RLI", '\u2068' to "FSI", '\u2069' to "PDI"
    )

    fun clean(text: String): String = text.filterNot { it in names }
    fun inspect(text: String): String = buildString {
        text.forEach { append(names[it]?.let { name -> "[$name]" } ?: it.toString()) }
    }

    fun nickname(base: String, ending: String): String {
        // Reverse code points, preserving surrogate pairs. Intended for short plain-text endings.
        val reversed = StringBuilder(clean(ending)).reverse().toString()
        return clean(base) + " \u2067\u202D\u2067" + reversed + "\u2067\u202D\u00A0"
    }

    fun closure(context: String): String {
        val stack = mutableListOf<Char>()
        for (c in context) when (c) {
            '\n', '\r', '\u0085', '\u001C', '\u001D', '\u001E', '\u2029' -> stack.clear()
            '\u2066', '\u2067', '\u2068' -> stack.add('i')
            '\u202A', '\u202B', '\u202D', '\u202E' -> stack.add('e')
            '\u202C' -> if (stack.lastOrNull() == 'e') stack.removeAt(stack.lastIndex)
            '\u2069' -> {
                val index = stack.lastIndexOf('i')
                if (index >= 0) stack.subList(index, stack.size).clear()
            }
        }
        return buildString { stack.asReversed().forEach { append(if (it == 'i') '\u2069' else '\u202C') } }
    }

    fun protect(nickname: String, body: String, strong: Boolean = false): String {
        val prefix = closure(nickname) + if (strong) "\u2069\u202C".repeat(3) else ""
        return prefix + buildString {
            append('\u2066')
            clean(body).forEach { c ->
                if (c in "\n\r\u0085\u001C\u001D\u001E\u2029") {
                    append('\u2069'); append(c); append('\u2066')
                } else append(c)
            }
            append('\u2069')
        }
    }
}
