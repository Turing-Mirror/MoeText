package com.turingmirror.moetext.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.turingmirror.moetext.data.ConfigStore
import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.engine.ChatDraft

/** Event types, flags and target packages are declared in res/xml/accessibility_service_config.xml. */
class MoeAccessibilityService : AccessibilityService() {
    companion object {
        /** Set by the live instance only, so a late teardown of an old instance cannot clear it. */
        @Volatile private var owner: Any? = null
        val connected: Boolean get() = owner != null
        private val logs = DiagLog(60)
        private fun diag(message: String) = logs.add(message)
        fun snapshotLogs(): List<String> = logs.snapshot()
        private const val QUIET_MS = 120L
        private const val BURST_MS = 1800L
        private const val POLL_MS = 100L
        private const val COMPOSING_POLL_MS = 60L
        private const val DISCOVERY_MS = 200L
        private const val SELECTION_GRACE_MS = 200L
        /** A text content event this close to a text event or a write belongs to that change. */
        private const val ECHO_MS = 300L
        private const val MAX_RETRIES = 3
    }

    private val token = Any()
    private val handler = Handler(Looper.getMainLooper())
    private val draft = ChatDraft()
    private var config = AppConfig()
    private var configDirty = true
    private var editor: AccessibilityNodeInfo? = null
    private var editorIdentity: AccessibilityNodeInfo? = null
    /** Latest text announced by the bound composer's own text events. */
    private var eventText: String? = null
    private var eventTextAt = 0L
    /** Composer ids found through the layout fallback, used for later lookups by id. */
    private val learnedIds = HashMap<String, MutableSet<String>>()
    private var observed = ""
    private var changedAt = 0L
    private var burstUntil = 0L
    private var retries = 0
    private var queuedAt = Long.MAX_VALUE
    private var lastRenderedText: String? = null
    private var lastComplete = false
    private var lastConfig: AppConfig? = null
    private var outsideTarget = false
    private var pendingSelection: ChatDraft.Result? = null
    private var selectionDeadline = 0L
    private val work = Runnable { queuedAt = Long.MAX_VALUE; process() }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        configDirty = true
        lastRenderedText = null
        schedule(0)
    }

    override fun onServiceConnected() {
        owner = token
        getSharedPreferences(ConfigStore.PREFS, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(preferenceListener)
        diag("connected: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() !in ChatTargets.packages) return
        try {
            val content = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            val textContent = content &&
                event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT != 0
            // Other content events come from message lists and animations, never from the composer.
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && (!content || textContent)) {
                inspectSource(event)
            }
            if (!content) burstUntil = SystemClock.uptimeMillis() + BURST_MS
            // Content events still cover focus changes that omit text events, at a lower rate.
            schedule(if (content && !textContent) DISCOVERY_MS else 0)
        } catch (e: Exception) {
            diag("event: ${e.javaClass.simpleName}")
        }
    }

    private fun inspectSource(event: AccessibilityEvent) {
        val source = event.source ?: return
        try {
            if (isComposer(source)) bind(source)
            if (source != editorIdentity) return
            val now = SystemClock.uptimeMillis()
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                eventText = ComposerText.afterChange(event.text, event.beforeText, event.removedCount, event.addedCount)
                eventTextAt = now
            } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - eventTextAt > ECHO_MS) {
                // The app replaced the text without a text event, e.g. a restored chat draft.
                eventText = null
            }
        } finally { source.recycle() }
    }

    private fun bind(node: AccessibilityNodeInfo) {
        if (editorIdentity != node) {
            draft.reset()
            observed = ""
            eventText = null
            lastRenderedText = null
            lastConfig = null
            pendingSelection = null
            retries = 0
            editorIdentity?.recycle()
            editorIdentity = AccessibilityNodeInfo.obtain(node)
            diag("editor: ${node.packageName} id=${node.viewIdResourceName?.substringAfterLast('/')} via ${match(node)?.name?.lowercase()}")
        }
        editor?.recycle()
        editor = AccessibilityNodeInfo.obtain(node)
    }

    private fun schedule(delay: Long) {
        val at = SystemClock.uptimeMillis() + delay
        if (at >= queuedAt) return
        handler.removeCallbacks(work)
        queuedAt = at
        handler.postAtTime(work, at)
    }

    private fun process() {
        if (owner !== token) return
        try {
            if (configDirty) {
                config = ConfigStore.load(this)
                configDirty = false
            }
            val node = findEditor()
            if (node == null) {
                // Without a composer seen before there is nothing to retry, only an app screen without chat.
                if (outsideTarget) stopEditor() else if (editorIdentity != null) retry("editor unavailable")
                return
            }
            val rich = readText(node) ?: run { retry("text unavailable"); return }
            val text = rich.toString()
            val now = SystemClock.uptimeMillis()
            pendingSelection?.let { expected ->
                if (text == expected.text) {
                    if (now <= selectionDeadline && node.textSelectionStart == text.length &&
                        node.textSelectionEnd == text.length) restoreSelection(node, expected)
                    pendingSelection = null
                } else if (now > selectionDeadline) pendingSelection = null
            }
            if (text != observed) {
                observed = text
                changedAt = now
                retries = 0
                burstUntil = now + BURST_MS
                draft.observe(text)
            }
            if (text.isEmpty()) {
                draft.reset()
                lastRenderedText = null
                return
            }
            if (ComposerText.isComposing(rich)) {
                diag("input: composing")
                if (now < burstUntil) schedule(COMPOSING_POLL_MS)
                return
            }
            if (ComposerText.hasRichTokens(rich)) {
                diag("input: rich mention")
                return
            }
            val complete = config.realtimeMode || now - changedAt >= QUIET_MS
            if (lastRenderedText == text && lastComplete == complete && lastConfig == config) {
                if (now < burstUntil) schedule(POLL_MS)
                return
            }
            val start = node.textSelectionStart
            val end = node.textSelectionEnd
            val result = draft.render(config, complete, start, end)
            if (result.text == text) {
                lastRenderedText = text
                lastComplete = complete
                lastConfig = config
            } else if (!write(node, text, start, end, result, complete, now)) {
                return
            }
            if (!complete) schedule((QUIET_MS - (now - changedAt)).coerceAtLeast(1))
            else if (now < burstUntil) schedule(POLL_MS)
        } catch (e: Exception) {
            retry(e.javaClass.simpleName)
        }
    }

    /** Writes [result] if the composer still shows [text] with the same selection; false schedules a retry. */
    private fun write(node: AccessibilityNodeInfo, text: String, start: Int, end: Int,
        result: ChatDraft.Result, complete: Boolean, now: Long): Boolean {
        // Read again immediately before writing; never overwrite a newer edit or moved caret.
        if (!node.refresh() || !isComposer(node) || readText(node)?.toString() != text ||
            node.textSelectionStart != start || node.textSelectionEnd != end ||
            ComposerText.isComposing(node.text) || ComposerText.hasRichTokens(node.text)) {
            retry("changed")
            return false
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, result.text)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            retry("setText")
            return false
        }
        draft.written()
        // ROMs that hide node text only report the write through a later text event.
        eventText = result.text
        eventTextAt = now
        pendingSelection = result
        selectionDeadline = now + SELECTION_GRACE_MS
        lastRenderedText = null
        if (!node.refresh() || !isComposer(node) || readText(node)?.toString() != result.text) {
            retry("verify")
            return false
        }
        draft.observe(result.text)
        observed = result.text
        lastRenderedText = result.text
        lastComplete = complete
        lastConfig = config
        restoreSelection(node, result)
        pendingSelection = null
        retries = 0
        diag("write: verified")
        return true
    }

    /**
     * Null node text means an empty composer without a hint, or a ROM that hides node text.
     * The caret tells them apart; hidden text falls back to the composer's own text events.
     */
    private fun readText(node: AccessibilityNodeInfo): CharSequence? {
        if (Build.VERSION.SDK_INT >= 26 && node.isShowingHintText) return ""
        node.text?.let { return it }
        val caret = maxOf(node.textSelectionStart, node.textSelectionEnd)
        if (caret <= 0) return ""
        return eventText?.takeIf { caret <= it.length }
    }

    private fun retry(reason: String) {
        diag("retry: $reason")
        if (retries < MAX_RETRIES) {
            retries++
            schedule(40L * retries)
        } else if (SystemClock.uptimeMillis() < burstUntil) {
            // Fast retries are spent but input is still active: keep a slow poll until the burst ends.
            schedule(POLL_MS * 3)
        }
    }

    private fun restoreSelection(node: AccessibilityNodeInfo, result: ChatDraft.Result) {
        if (result.selectionStart < 0 || result.selectionEnd < 0) return
        val selection = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, result.selectionStart)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, result.selectionEnd)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
    }

    private fun isComposer(node: AccessibilityNodeInfo): Boolean = match(node) != null

    private fun match(node: AccessibilityNodeInfo): ComposerMatch? {
        // Focus is not required: on ColorOS and MIUI the focus flag flickers after a write.
        val pkg = node.packageName?.toString()
        if (!node.isEditable || !node.isEnabled || node.isPassword || !node.isVisibleToUser ||
            pkg !in ChatTargets.packages) return null
        val label = if (Build.VERSION.SDK_INT >= 26) node.hintText?.toString()?.takeIf { it.isNotBlank() } else null
        val description = label ?: node.contentDescription?.toString()
        ChatTargets.match(pkg, node.viewIdResourceName, description)?.let { return it }
        if (!ChatTargets.usesLayoutFallback(pkg) || !node.isMultiLine) return null
        val bounds = Rect().also(node::getBoundsInScreen)
        val window = windowBounds(node)
        if (!ChatTargets.matchesLayout(pkg, description, node.className?.toString(), node.isMultiLine,
                bounds.top, window.top, window.bottom)) return null
        node.viewIdResourceName?.let { id ->
            if (learnedIds.getOrPut(pkg!!) { HashSet() }.add(id)) diag("learned id: ${id.substringAfterLast('/')}")
        }
        return ComposerMatch.LAYOUT
    }

    private fun windowBounds(node: AccessibilityNodeInfo): Rect {
        val bounds = Rect()
        node.window?.let { window ->
            window.getBoundsInScreen(bounds)
            if (Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") window.recycle()
        }
        if (bounds.isEmpty) resources.displayMetrics.let { bounds.set(0, 0, it.widthPixels, it.heightPixels) }
        return bounds
    }

    private fun findEditor(): AccessibilityNodeInfo? {
        outsideTarget = false
        // The bound composer costs one refresh; the window scan below costs one call per window.
        editor?.let { cached ->
            if (cached.refresh() && isComposer(cached)) return cached
            cached.recycle()
            editor = null
        }
        var sawWindow = false
        var sawTarget = false
        val active = rootInActiveWindow
        try {
            if (active != null) {
                sawWindow = true
                if (active.packageName?.toString() in ChatTargets.packages) {
                    sawTarget = true
                    locate(active)?.let { return it }
                }
            }
            // A keyboard or popup can hold the active window while the chat window stays on screen.
            for (window in windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || window.id == active?.windowId) continue
                val root = window.root ?: continue
                try {
                    sawWindow = true
                    if (root.packageName?.toString() !in ChatTargets.packages) continue
                    sawTarget = true
                    locate(root)?.let { return it }
                } finally { root.recycle() }
            }
        } finally { active?.recycle() }
        outsideTarget = sawWindow && !sawTarget
        return null
    }

    private fun locate(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
            try {
                if (isComposer(focused)) { bind(focused); return editor }
            } finally { focused.recycle() }
        }
        // Some ROMs stop reporting input focus while the composer is still on screen.
        val pkg = root.packageName?.toString() ?: return null
        val ids = ChatTargets.viewIdsFor(pkg) + learnedIds[pkg].orEmpty()
        for (id in ids) {
            val candidates = root.findAccessibilityNodeInfosByViewId(id) ?: continue
            var hit: AccessibilityNodeInfo? = null
            for (c in candidates) {
                if (hit == null && isComposer(c)) hit = c else c.recycle()
            }
            if (hit != null) {
                try { bind(hit) } finally { hit.recycle() }
                return editor
            }
        }
        return null
    }

    private fun stopEditor(clearDraft: Boolean = false) {
        handler.removeCallbacks(work)
        queuedAt = Long.MAX_VALUE
        burstUntil = 0
        editor?.recycle()
        editor = null
        pendingSelection = null
        if (clearDraft) {
            editorIdentity?.recycle()
            editorIdentity = null
            eventText = null
            draft.reset()
            observed = ""
            lastRenderedText = null
        }
    }

    override fun onInterrupt() {
        diag("interrupt")
        stopEditor()
    }

    private fun shutdown() {
        if (owner !== token) return
        owner = null
        getSharedPreferences(ConfigStore.PREFS, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(preferenceListener)
        stopEditor(clearDraft = true)
        diag("disconnected")
    }

    override fun onUnbind(intent: Intent?): Boolean { shutdown(); return super.onUnbind(intent) }
    override fun onDestroy() { shutdown(); super.onDestroy() }
}
