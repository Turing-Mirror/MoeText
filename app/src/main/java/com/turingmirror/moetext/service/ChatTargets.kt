package com.turingmirror.moetext.service

/** App-specific composer recognition, shared by event and snapshot handling. */
object ChatTargets {
    val packages = setOf("com.tencent.mobileqq", "com.tencent.mobileqqi", "com.discord")
    private val discordIds = setOf("chat_input_edit_text", "chat_input", "chat_input_text_input", "message_input")

    fun matches(packageName: String?, resourceId: String?, label: String?): Boolean {
        if (packageName !in packages) return false
        if (packageName != "com.discord") return resourceId == "$packageName:id/input"
        val hint = label.orEmpty().trim().lowercase()
        if (hint.contains("search") || hint.contains("搜索") || hint.contains("搜尋")) return false
        return resourceId?.substringAfterLast('/') in discordIds ||
            hint.startsWith("message ") || hint == "message" ||
            hint.startsWith("发消息") || hint.startsWith("發訊息") ||
            hint.startsWith("发送消息") || hint.startsWith("傳送訊息") ||
            hint.startsWith("发信息") || hint.startsWith("向 #") || hint.startsWith("向 @")
    }

    /**
     * 各目标应用输入框的完整 viewId，给 `findAccessibilityNodeInfosByViewId` 兜底用。
     * 焦点标志在部分 ROM 上不可靠 —— 输入框仍在，但不再报 FOCUS_INPUT。
     */
    fun viewIdsFor(packageName: String?): List<String> = when (packageName) {
        "com.discord" -> discordIds.map { "$packageName:id/$it" }
        in packages -> listOf("$packageName:id/input")
        else -> emptyList()
    }
}
