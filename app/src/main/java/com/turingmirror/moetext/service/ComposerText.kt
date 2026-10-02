package com.turingmirror.moetext.service

import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import com.turingmirror.moetext.engine.TextUnits

/** Reads composer text from accessibility payloads. */
internal object ComposerText {
    /**
     * Text after a TYPE_VIEW_TEXT_CHANGED event, or null when the payload is incomplete.
     * An emptied editor reports its hint as the event text; the counts reveal that case.
     */
    fun afterChange(items: List<CharSequence?>, before: CharSequence?, removed: Int, added: Int): String? {
        val current = items.joinToString("") { it ?: "" }
        if (before == null || removed < 0 || added < 0) return current.takeIf { items.isNotEmpty() }
        val expected = before.length - removed + added
        return when {
            current.length == expected -> current
            expected == 0 -> ""
            else -> null
        }
    }

    fun isComposing(text: CharSequence?): Boolean = text is Spanned &&
        text.getSpans(0, text.length, Any::class.java).any {
            text.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0
        }

    /**
     * Mention chips and similar rich tokens; writing plain text would discard their metadata.
     * Spans that only draw emoji or QQ faces are rebuilt by the app from plain text.
     */
    fun hasRichTokens(text: CharSequence?): Boolean {
        if (text !is Spanned) return false
        if (text.getSpans(0, text.length, ClickableSpan::class.java).isNotEmpty()) return true
        return text.getSpans(0, text.length, ReplacementSpan::class.java).any { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            start in 0 until end && !TextUnits.isPictographic(text.substring(start, end))
        }
    }
}
