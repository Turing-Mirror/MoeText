package com.turingmirror.moetext.service

/** Bounded diagnostic log; consecutive repeats collapse into one line with a count. */
internal class DiagLog(private val capacity: Int) {
    private val lines = ArrayDeque<String>()
    private var last: String? = null
    private var repeats = 1

    @Synchronized
    fun add(message: String) {
        if (message == last && lines.isNotEmpty()) {
            repeats++
            lines[lines.lastIndex] = "$message ×$repeats"
            return
        }
        last = message
        repeats = 1
        lines.addLast(message)
        while (lines.size > capacity) lines.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()
}
