package com.turingmirror.moetext.service

/** How a node was recognized as a chat composer. */
enum class ComposerMatch { ID, HINT, LAYOUT }

/**
 * One chat app. [composerIds] are resource entry names. [layoutFallback] also accepts a
 * multi-line text field docked in the lower part of the window, for builds whose
 * resource names are shortened or renamed. Messages containing [mentionMarker] stay
 * untouched, because plain text written back would drop the app's mention data.
 */
data class ChatApp(
    val packages: Set<String>,
    val composerIds: Set<String>,
    val hintPrefixes: List<String> = emptyList(),
    val layoutFallback: Boolean = false,
    val mentionMarker: Char? = null
)

/** App-specific composer recognition, shared by event and snapshot handling. */
object ChatTargets {
    private val apps = listOf(
        ChatApp(
            packages = setOf("com.tencent.mobileqq", "com.tencent.mobileqqi"),
            composerIds = setOf("input"),
            layoutFallback = true,
            mentionMarker = '@'
        ),
        ChatApp(
            packages = setOf("com.discord"),
            composerIds = setOf("chat_input_edit_text", "chat_input", "chat_input_text_input", "message_input"),
            hintPrefixes = listOf("message ", "发消息", "發訊息", "发送消息", "傳送訊息", "发信息", "向 #", "向 @")
        )
    )
    private val excludedHints = listOf("search", "搜索", "搜尋")

    /** A layout match must start below this fraction of its window height. */
    private const val LAYOUT_MIN_TOP = 0.45f

    val packages: Set<String> = apps.flatMap { it.packages }.toSet()

    private fun appFor(packageName: String?): ChatApp? = apps.firstOrNull { packageName in it.packages }

    private fun normalized(label: String?) = label.orEmpty().trim().lowercase()

    fun match(packageName: String?, resourceId: String?, label: String?): ComposerMatch? {
        val app = appFor(packageName) ?: return null
        val hint = normalized(label)
        if (excludedHints.any { hint.contains(it) }) return null
        if (resourceId?.substringAfterLast('/') in app.composerIds) return ComposerMatch.ID
        if (app.hintPrefixes.any { hint == it.trimEnd() || hint.startsWith(it) }) return ComposerMatch.HINT
        return null
    }

    fun holdsMention(packageName: String?, text: String): Boolean =
        appFor(packageName)?.mentionMarker?.let { it in text } == true

    fun usesLayoutFallback(packageName: String?): Boolean = appFor(packageName)?.layoutFallback == true

    fun matchesLayout(packageName: String?, label: String?, className: String?, multiLine: Boolean,
        top: Int, windowTop: Int, windowBottom: Int): Boolean {
        if (!usesLayoutFallback(packageName)) return false
        if (!multiLine || className?.endsWith("EditText") != true) return false
        val hint = normalized(label)
        if (excludedHints.any { hint.contains(it) }) return false
        val height = windowBottom - windowTop
        return height > 0 && top - windowTop >= height * LAYOUT_MIN_TOP
    }

    /** Full view ids for `findAccessibilityNodeInfosByViewId`, used when focus reporting is unreliable. */
    fun viewIdsFor(packageName: String?): List<String> =
        appFor(packageName)?.composerIds?.map { "$packageName:id/$it" } ?: emptyList()
}
