package com.turingmirror.moetext.service

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ChatTargetsTest {
    private val qq = "com.tencent.mobileqq"

    @Test fun discordComposerRecognitionExcludesSearchAndOtherApps() {
        assertEquals(ComposerMatch.HINT, ChatTargets.match("com.discord", null, "Message #general"))
        assertEquals(ComposerMatch.HINT, ChatTargets.match("com.discord", null, "发送消息给 @用户"))
        assertNull(ChatTargets.match("com.discord", null, "Search messages"))
        assertNull(ChatTargets.match("com.discord", null, "修改昵称"))
        assertNull(ChatTargets.match("com.example", null, "Message #general"))
        assertNull(ChatTargets.match(qq, "$qq:id/search", "Message"))
    }

    @Test fun qqComposerMatchesItsIdInAnyResourceNamespace() {
        assertEquals(ComposerMatch.ID, ChatTargets.match(qq, "$qq:id/input", null))
        assertEquals(ComposerMatch.ID, ChatTargets.match("com.tencent.mobileqqi", "com.tencent.mobileqqi:id/input", null))
        assertNull(ChatTargets.match(qq, "$qq:id/input", "搜索"))
    }

    @Test fun qqLayoutFallbackAcceptsOnlyADockedMultiLineField() {
        fun layout(label: String? = null, className: String = "android.widget.EditText", multiLine: Boolean = true,
            top: Int = 1500, pkg: String = qq) = ChatTargets.matchesLayout(pkg, label, className, multiLine, top, 0, 2400)
        assertTrue(layout())
        assertFalse(layout(top = 200)) // search bars and profile fields sit near the top
        assertFalse(layout(multiLine = false))
        assertFalse(layout(label = "搜索"))
        assertFalse(layout(className = "android.widget.TextView"))
        assertFalse(layout(pkg = "com.discord"))
    }

    @Test fun viewIdsAreQualifiedWithThePackage() {
        assertEquals(listOf("$qq:id/input"), ChatTargets.viewIdsFor(qq))
        assertTrue(ChatTargets.viewIdsFor("com.example").isEmpty())
    }

    @Test fun serviceConfigDeclaresExactlyTheTargetPackages() {
        val xml = File("src/main/res/xml/accessibility_service_config.xml").readText()
        val declared = Regex("android:packageNames=\"([^\"]*)\"").find(xml)!!.groupValues[1].split(',').toSet()
        assertEquals(ChatTargets.packages, declared)
    }
}
