package com.turingmirror.moetext.engine

/** Character runs that an editor renders as one unit; rules must never match inside them. */
object TextUnits {
    /** QQ stores a built-in face as U+0014 followed by one face-id character, which may be punctuation. */
    private val atomic = listOf(Regex("\\u0014[\\s\\S]"))
    private const val PLACEHOLDER = '￼'

    /** Same-length copy with atomic units replaced by a neutral placeholder. */
    fun mask(text: String): String {
        var chars: CharArray? = null
        for (pattern in atomic) for (match in pattern.findAll(text)) {
            val target = chars ?: text.toCharArray().also { chars = it }
            for (i in match.range) target[i] = PLACEHOLDER
        }
        return chars?.let(::String) ?: text
    }

    /** True when [text] holds only pictures such as emoji or faces, with no letters or digits. */
    fun isPictographic(text: String): Boolean =
        mask(text).none { it != PLACEHOLDER && it.isLetterOrDigit() }
}
