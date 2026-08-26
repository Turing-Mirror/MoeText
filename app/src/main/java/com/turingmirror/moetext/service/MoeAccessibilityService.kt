package com.turingmirror.moetext.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.util.Log
import com.turingmirror.moetext.data.ConfigStore
import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.engine.Stripper
import com.turingmirror.moetext.engine.TransformEngine

class MoeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MoeTextSvc"
        private const val PKG_QQ = "com.tencent.mobileqq"
        private const val PKG_QQI = "com.tencent.mobileqqi"
        private const val ID_INPUT = "com.tencent.mobileqq:id/input"
        private const val ID_SEND = "com.tencent.mobileqq:id/send_btn"
        private const val ECHO_WINDOW_MS = 600L
        private val TRIGGER_ENDS = setOf('。', '！', '!', '？', '?')
    }

    private var config: AppConfig = AppConfig()
    private var userOriginal = ""
    private var lastTarget = ""
    private var lastWriteAt = 0L
    private var busy = false

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 80
            packageNames = arrayOf(PKG_QQ, PKG_QQI)
        }
        resetSession()
    }

    override fun onInterrupt() {
        busy = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg != PKG_QQ && pkg != PKG_QQI) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> resetSession()
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTextChanged()
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (event.source?.viewIdResourceName == ID_SEND) process(allowRandomTail = true)
            }
        }
    }

    private fun resetSession() {
        userOriginal = ""
        lastTarget = ""
        lastWriteAt = 0L
        busy = false
        config = ConfigStore.load(this)
    }

    private fun handleTextChanged() {
        if (!config.realtimeMode) {
            val current = peekInputText() ?: return
            if (current.isEmpty() || current.last() !in TRIGGER_ENDS) return
        }
        process(allowRandomTail = false)
    }

    private fun process(allowRandomTail: Boolean) {
        if (busy) return
        busy = true
        var node: AccessibilityNodeInfo? = null
        try {
            val root = rootInActiveWindow ?: return
            node = findNodeById(root, ID_INPUT) ?: findEditable(root)
            root.recycle()
            node ?: return

            val raw = node.text?.toString()?.trim() ?: ""
            if (raw.isEmpty()) {
                clearAccumulator()
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastWriteAt < ECHO_WINDOW_MS && raw == lastTarget) {
                lastWriteAt = 0L
                return
            }

            recoverOriginal(raw)
            if (userOriginal.isEmpty()) return

            val target = TransformEngine.transform(userOriginal, config, allowRandomTail)
            if (target != raw) {
                if (setText(node, target)) {
                    lastTarget = target
                    lastWriteAt = SystemClock.elapsedRealtime()
                }
            } else {
                lastTarget = target
            }
        } catch (e: Exception) {
            Log.d(TAG, "process failed", e)
        } finally {
            busy = false
            node?.recycle()
        }
    }

    private fun recoverOriginal(raw: String) {
        if (lastTarget.isNotEmpty() && raw.startsWith(lastTarget)) {
            userOriginal += raw.substring(lastTarget.length)
        } else {
            userOriginal = Stripper.strip(raw, config)
        }
    }

    private fun clearAccumulator() {
        userOriginal = ""
        lastTarget = ""
    }

    private fun peekInputText(): String? {
        val root = rootInActiveWindow ?: return null
        val node = findNodeById(root, ID_INPUT) ?: findEditable(root)
        root.recycle()
        val text = node?.text?.toString()?.trim()
        node?.recycle()
        return text
    }

    private fun findNodeById(node: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        if (id == node.viewIdResourceName) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeById(child, id)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok) {
                val sel = Bundle()
                sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
                sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
            }
            ok
        } catch (e: Exception) {
            false
        }
    }
}
