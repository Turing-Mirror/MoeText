package com.turingmirror.moetext.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.turingmirror.moetext.data.ConfigStore
import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.engine.ChatDraft

class MoeAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var connected = false
            private set
        private val logs = ArrayDeque<String>()
        private fun diag(message: String) = synchronized(logs) {
            logs.addLast(message)
            while (logs.size > 60) logs.removeFirst()
        }
        fun snapshotLogs(): List<String> = synchronized(logs) { logs.toList() }
        private const val QUIET_MS = 120L
        private const val BURST_MS = 1800L
        private const val POLL_MS = 100L
        private const val MAX_RETRIES = 3
    }

    private val handler = Handler(Looper.getMainLooper())
    private val draft = ChatDraft()
    private var config = AppConfig()
    private var configDirty = true
    private var editor: AccessibilityNodeInfo? = null
    private var editorIdentity: AccessibilityNodeInfo? = null
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
        connected = true
        getSharedPreferences("moetext_config", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(preferenceListener)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 0
            packageNames = ChatTargets.packages.toTypedArray()
        }
        diag("connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() !in ChatTargets.packages) return
        try {
            val source = event.source
            if (source != null) {
                try {
                    if (isComposer(source)) bind(source)
                } finally { source.recycle() }
            }
            // Coalesce callbacks on the main queue without an extra fixed input delay.
            // Content events also cover focus transitions that omit text events.
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || editor == null) {
                burstUntil = SystemClock.uptimeMillis() + BURST_MS
            }
            schedule(0)
        } catch (e: Exception) {
            diag("event: ${e.javaClass.simpleName}")
        }
    }

    private fun bind(node: AccessibilityNodeInfo) {
        if (editorIdentity != node) {
            draft.reset()
            observed = ""
            lastRenderedText = null
            lastConfig = null
            pendingSelection = null
            retries = 0
            editorIdentity?.recycle()
            editorIdentity = AccessibilityNodeInfo.obtain(node)
            diag("editor: ${node.packageName}")
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
        if (!connected) return
        try {
            if (configDirty) {
                config = ConfigStore.load(this)
                configDirty = false
            }
            val node = findEditor()
            if (node == null) {
                if (outsideTarget) stopEditor() else retry("editor unavailable")
                return
            }
            val rich = if (Build.VERSION.SDK_INT >= 26 && node.isShowingHintText) ""
                else node.text ?: run { retry("text unavailable"); return }
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
            if (isComposing(rich)) {
                diag("input: composing")
                if (now < burstUntil) schedule(60)
                return
            }
            // Replacing a rich mention chip with a String would discard its metadata.
            if (hasMentionSpans(rich)) {
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
            } else {
                // Read again immediately before writing; never overwrite a newer edit or moved caret.
                if (!node.refresh() || !isComposer(node) || node.text?.toString() != text ||
                    node.textSelectionStart != start || node.textSelectionEnd != end ||
                    isComposing(node.text) || hasMentionSpans(node.text)) {
                    retry("changed")
                    return
                }
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, result.text)
                }
                if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    retry("setText")
                    return
                }
                draft.written()
                pendingSelection = result
                selectionDeadline = now + 200
                lastRenderedText = null
                if (node.refresh() && isComposer(node) && node.text?.toString() == result.text) {
                    draft.observe(result.text)
                    observed = result.text
                    lastRenderedText = result.text
                    lastComplete = complete
                    lastConfig = config
                    restoreSelection(node, result)
                    pendingSelection = null
                    retries = 0
                    diag("write: verified")
                } else {
                    retry("verify")
                    return
                }
            }
            if (!complete) schedule((QUIET_MS - (now - changedAt)).coerceAtLeast(1))
            else if (now < burstUntil) schedule(POLL_MS)
        } catch (e: Exception) {
            retry(e.javaClass.simpleName)
        }
    }

    private fun retry(reason: String) {
        diag("retry: $reason")
        if (retries < MAX_RETRIES) {
            retries++
            schedule(40L * retries)
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

    private fun isComposing(text: CharSequence?): Boolean = text is Spanned &&
        text.getSpans(0, text.length, Any::class.java).any {
            text.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0
        }

    private fun hasMentionSpans(text: CharSequence?): Boolean = text is Spanned &&
        (text.getSpans(0, text.length, ReplacementSpan::class.java).isNotEmpty() ||
            text.getSpans(0, text.length, ClickableSpan::class.java).isNotEmpty())

    private fun isComposer(node: AccessibilityNodeInfo): Boolean {
        if (!node.isEditable || !node.isFocused || !node.isEnabled || node.isPassword ||
            !node.isVisibleToUser || node.packageName?.toString() !in ChatTargets.packages) return false
        val label = if (Build.VERSION.SDK_INT >= 26) node.hintText?.toString()?.takeIf { it.isNotBlank() } else null
        return ChatTargets.matches(node.packageName?.toString(), node.viewIdResourceName,
            label ?: node.contentDescription?.toString())
    }

    private fun findEditor(): AccessibilityNodeInfo? {
        outsideTarget = false
        val root = rootInActiveWindow ?: return null
        try {
            if (root.packageName?.toString() !in ChatTargets.packages) {
                outsideTarget = true
                return null
            }
            editor?.let {
                if (it.windowId == root.windowId && it.refresh() && isComposer(it)) return it
            }
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                try {
                    if (isComposer(focused)) { bind(focused); return editor }
                } finally { focused.recycle() }
            }
            return null
        } finally { root.recycle() }
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
            draft.reset()
            observed = ""
            lastRenderedText = null
        }
    }

    override fun onInterrupt() { stopEditor() }

    private fun shutdown() {
        connected = false
        getSharedPreferences("moetext_config", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(preferenceListener)
        stopEditor(clearDraft = true)
    }

    override fun onUnbind(intent: Intent?): Boolean { shutdown(); return super.onUnbind(intent) }
    override fun onDestroy() { shutdown(); super.onDestroy() }
}
