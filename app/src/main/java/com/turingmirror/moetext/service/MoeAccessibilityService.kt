package com.turingmirror.moetext.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.turingmirror.moetext.data.ConfigStore
import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.engine.Stripper
import com.turingmirror.moetext.engine.TransformEngine

class MoeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MoeTextSvc"
        private const val PKG_QQ = "com.tencent.mobileqq"
        private const val PKG_QQI = "com.tencent.mobileqqi"
        private const val INPUT_ID_SUFFIX = ":id/input"
        private const val SEND_ID_TOKEN = "send"
        private const val REALTIME_DEBOUNCE_MS = 110L
        private const val RECONCILE_ROUNDS = 4
        private val TRIGGER_ENDS = setOf('。', '！', '!', '？', '?')

        private val LOG_LOCK = Any()
        private val LOG_RING = ArrayDeque<String>()
        private const val LOG_CAP = 60

        fun snapshotLogs(): List<String> = synchronized(LOG_LOCK) { LOG_RING.toList() }

        private fun diag(line: String) {
            synchronized(LOG_LOCK) {
                LOG_RING.addLast(line)
                while (LOG_RING.size > LOG_CAP) LOG_RING.removeFirst()
            }
            Log.d(TAG, line)
        }
    }

    private var config: AppConfig = AppConfig()
    private var userOriginal = ""
    private var lastTarget = ""
    private var seqIndex = 0
    private var cachedInput: AccessibilityNodeInfo? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var workRunning = false
    private var workQueued = false
    private var workRequestedWhileRunning = false
    private var queuedForceTail = false

    override fun onServiceConnected() {
        diag("connected")
        try {
            serviceInfo = serviceInfo.apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                notificationTimeout = 80
                packageNames = arrayOf(PKG_QQ, PKG_QQI)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "configure serviceInfo failed", t)
        }
        reloadConfig()
    }

    override fun onInterrupt() {
        workRunning = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val pkg = event.packageName?.toString() ?: return
            if (pkg != PKG_QQ && pkg != PKG_QQI) return
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    diag("evt winState $pkg")
                    resetSession()
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> requestWork(forceTail = false)
                AccessibilityEvent.TYPE_VIEW_CLICKED -> handlePossibleSend(event)
            }
        } catch (t: Throwable) {
            diag("dispatch fail: ${t.javaClass.simpleName}")
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        dropCachedInput()
        workQueued = false
        workRunning = false
    }

    private fun reloadConfig() {
        config = try {
            ConfigStore.load(this)
        } catch (t: Throwable) {
            AppConfig()
        }
    }

    private fun resetSession() {
        userOriginal = ""
        lastTarget = ""
        seqIndex = 0
        dropCachedInput()
        reloadConfig()
    }

    private fun requestWork(forceTail: Boolean) {
        if (workRunning) {
            workRequestedWhileRunning = true
            queuedForceTail = queuedForceTail || forceTail
            return
        }
        if (workQueued) {
            queuedForceTail = queuedForceTail || forceTail
            return
        }
        workQueued = true
        val delay = if (config.realtimeMode && !forceTail) REALTIME_DEBOUNCE_MS else 0L
        mainHandler.postDelayed({
            workQueued = false
            runWorkCycle(forceTail = queuedForceTail.also { queuedForceTail = false })
        }, delay)
    }

    private fun runWorkCycle(forceTail: Boolean) {
        if (workRunning) return
        workRunning = true
        try {
            reloadConfig()
            val gate = shouldProcessNow(forceTail)
            if (!gate) {
                diag("skip: gate=false mode=${if (config.realtimeMode) "rt" else "punct"}")
                return
            }
            reconcile(forceTail)
        } catch (t: Throwable) {
            diag("cycle fail: ${t.javaClass.simpleName}")
            Log.d(TAG, "work cycle failed", t)
        } finally {
            workRunning = false
            if (workRequestedWhileRunning) {
                workRequestedWhileRunning = false
                requestWork(forceTail = queuedForceTail.also { queuedForceTail = false })
            }
        }
    }

    private fun shouldProcessNow(forceTail: Boolean): Boolean {
        if (forceTail) return true
        if (config.realtimeMode) return true
        val current = peekInputText() ?: return false
        if (current.isEmpty()) {
            clearAccumulator()
            return false
        }
        return current.last() in TRIGGER_ENDS
    }

    private fun handlePossibleSend(event: AccessibilityEvent) {
        val source = try {
            event.source
        } catch (t: Throwable) {
            null
        } ?: return
        val id = try {
            source.viewIdResourceName
        } catch (t: Throwable) {
            null
        }
        val isSend = id != null && id.substringAfterLast('/').contains(SEND_ID_TOKEN, ignoreCase = true)
        diag("click id=$id send=$isSend")
        if (!isSend) return
        requestWork(forceTail = true)
    }

    private fun reconcile(forceTail: Boolean) {
        val node = inputNode() ?: return
        for (round in 0 until RECONCILE_ROUNDS) {
            val current = currentText(node) ?: return
            val sameAsTarget = current == lastTarget
            if (sameAsTarget && !forceTail) return
            if (current.isEmpty()) {
                clearAccumulator()
                return
            }
            recoverOriginal(current)
            if (userOriginal.isEmpty()) return
            val target = TransformEngine.transform(userOriginal, config, forceTail, seqIndex)
            seqIndex++
            if (target == current) {
                lastTarget = target
                return
            }
            if (!setText(node, target)) {
                val ok = pasteViaClipboard(node, target)
                diag("setText: FAIL paste=$ok len=${target.length}")
                if (!ok) return
            }
            lastTarget = target
            if (sameAsTarget && !forceTail) return
            val fresh = if (node.refresh()) currentText(node) else null
            if (fresh == null || fresh == target) return
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

    private fun currentText(node: AccessibilityNodeInfo): String? = try {
        node.text?.toString()?.trim()
    } catch (t: Throwable) {
        null
    }

    private fun peekInputText(): String? {
        val node = inputNode() ?: return null
        return currentText(node)
    }

    private fun dropCachedInput() {
        cachedInput?.let { node ->
            try {
                node.recycle()
            } catch (ignored: Exception) {
            }
        }
        cachedInput = null
    }

    private fun inputNode(): AccessibilityNodeInfo? {
        cachedInput?.let { cached ->
            if (currentText(cached) != null) return cached
            dropCachedInput()
        }
        val root = rootInActiveWindow
        if (root == null) {
            diag("node: rootInActiveWindow=null winCnt=${try { windows?.size ?: -1 } catch (t: Throwable) { -1 }}")
            for (win in try { windows.orEmpty() } catch (t: Throwable) { emptyList<AccessibilityWindowInfo>() }) {
                val wr = win.root ?: continue
                val pkg = wr.packageName?.toString() ?: continue
                if (pkg.contains("tencent.mobileqq")) {
                    diag("node: try window $pkg")
                    val found = findNodeById(wr, INPUT_ID_SUFFIX) ?: findEditable(wr)
                    if (found != null) {
                        diag("node: fromWin id=${found.viewIdResourceName}")
                        cachedInput = found
                        return found
                    }
                }
            }
            return null
        }
        val node = findNodeById(root, INPUT_ID_SUFFIX) ?: findEditable(root)
        diag("node: root id=${node?.viewIdResourceName} editable=${node?.isEditable}")
        cachedInput = node
        return node
    }

    private fun findNodeById(root: AccessibilityNodeInfo, idSuffix: String): AccessibilityNodeInfo? {
        val pkg = root.packageName?.toString() ?: return null
        val matches = try {
            root.findAccessibilityNodeInfosByViewId(pkg + idSuffix)
        } catch (t: Throwable) {
            emptyList()
        }
        var result: AccessibilityNodeInfo? = null
        for (info in matches) {
            if (result == null) result = info else info.recycle()
        }
        return result
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return AccessibilityNodeInfo.obtain(root)
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = if (child.isEditable) child else findEditable(child)
            if (found != null) {
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun pasteViaClipboard(node: AccessibilityNodeInfo, text: String): Boolean = try {
        val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        cm.setPrimaryClip(ClipData.newPlainText("moetext", text))
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    } catch (t: Throwable) {
        false
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok) {
                try {
                    val sel = Bundle()
                    sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
                    sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
                } catch (ignored: Throwable) {
                }
            }
            ok
        } catch (t: Throwable) {
            false
        }
    }
}
