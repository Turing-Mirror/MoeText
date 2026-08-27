package com.turingmirror.moetext.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

    @Volatile
    private var lastObservedText: String? = null
    private var lastObservedAt = 0L
    private var configDirty = true

    private val mainHandler = Handler(Looper.getMainLooper())
    private var workRunning = false
    private var workQueued = false
    private var workRequestedWhileRunning = false
    private var queuedForceTail = false
    private var scheduledWork: Runnable? = null

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
                    dropCachedInput()
                    configDirty = true
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    val evText = try {
                        event.text?.joinToString("")
                    } catch (t: Throwable) {
                        null
                    }
                    val before = try {
                        event.beforeText?.toString()
                    } catch (t: Throwable) {
                        null
                    }
                    diag("txt ev=${evText?.length ?: -1} bf=${before?.length ?: -1}")
                    if (evText != null) {
                        lastObservedText = evText
                        lastObservedAt = SystemClock.elapsedRealtime()
                        if (evText.isEmpty()) {
                            clearAccumulator()
                        }
                    } else {
                        diag("txt: 当前文本不可读")
                    }
                    requestWork(forceTail = false)
                }
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
        scheduledWork = null
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

    private fun requestWork(forceTail: Boolean) {
        if (forceTail) {
            queuedForceTail = true
            if (workRunning) {
                workRequestedWhileRunning = true
                diag("req: busy final")
                return
            }
            scheduledWork?.let { mainHandler.removeCallbacks(it) }
            scheduledWork = null
            workQueued = false
            queuedForceTail = false
            diag("req: final immediate")
            runWorkCycle(forceTail = true)
            return
        }
        if (workRunning) {
            workRequestedWhileRunning = true
            diag("req: busy input")
            return
        }
        if (workQueued) {
            return
        }
        val runnable = Runnable {
            scheduledWork = null
            workQueued = false
            val final = queuedForceTail
            queuedForceTail = false
            diag("exec: fire final=$final")
            runWorkCycle(forceTail = final)
        }
        scheduledWork = runnable
        workQueued = true
        mainHandler.postDelayed(runnable, REALTIME_DEBOUNCE_MS)
    }

    private fun runWorkCycle(forceTail: Boolean) {
        if (workRunning) {
            workRequestedWhileRunning = true
            queuedForceTail = queuedForceTail || forceTail
            diag("BUSY-DROP final=$forceTail")
            return
        }
        workRunning = true
        try {
            if (configDirty) {
                reloadConfig()
                configDirty = false
            }
            val node = inputNode()
            val nodeText: String? = try {
                node?.let { currentText(it) }
            } catch (t: Throwable) {
                null
            }
            var current = nodeText?.takeIf { it.isNotEmpty() }
            if (current == null && nodeText == null && !lastObservedText.isNullOrEmpty()) {
                if (SystemClock.elapsedRealtime() - lastObservedAt <= 500L) {
                    val hint = lastObservedText
                    if (hint != null) {
                        current = hint
                        diag("cur: evtHint len=${hint.length}")
                    }
                }
            }
            if (current == null) {
                if (nodeText != null) clearAccumulator()
                diag("cur: empty (node=${node != null})")
                return
            }
            if (!forceTail && !config.realtimeMode && !endsWithTrigger(current)) {
                diag("skip: punct curLen=${current.length}")
                return
            }
            if (node == null) {
                diag("rec: no node")
                return
            }
            lastObservedText = current
            lastObservedAt = SystemClock.elapsedRealtime()
            reconcileNode(node, current, forceTail)
        } catch (t: Throwable) {
            diag("cycle fail: ${t.javaClass.simpleName}")
        } finally {
            workRunning = false
            if (workRequestedWhileRunning) {
                val rerunFinal = queuedForceTail
                workRequestedWhileRunning = false
                queuedForceTail = false
                requestWork(forceTail = rerunFinal)
            }
        }
    }

    private fun endsWithTrigger(text: String): Boolean =
        text.trimEnd().lastOrNull() in TRIGGER_ENDS

    private fun handlePossibleSend(event: AccessibilityEvent) {
        val source = try {
            event.source
        } catch (t: Throwable) {
            null
        }
        if (source == null) {
            diag("click src=null ignored")
            return
        }
        val id = try {
            source.viewIdResourceName
        } catch (t: Throwable) {
            null
        }
        val label = try {
            source.text?.toString() ?: source.contentDescription?.toString()
        } catch (t: Throwable) {
            null
        }
        val isSend = id != null && (
            id.substringAfterLast('/').contains(SEND_ID_TOKEN, ignoreCase = true) ||
                id.contains("chat_msg_send", ignoreCase = true)
            ) || label?.contains("发送") == true
        diag("click id=$id label=$label send=$isSend")
        if (!isSend) return
        requestWork(forceTail = true)
    }

    private fun reconcileNode(node: AccessibilityNodeInfo, initial: String, forceTail: Boolean) {
        var current = initial
        var advanced = false
        for (round in 0 until RECONCILE_ROUNDS) {
            val sameAsTarget = current == lastTarget
            if (sameAsTarget && !forceTail) return
            recoverOriginal(current)
            if (userOriginal.isEmpty()) {
                diag("orig: stripped empty")
                return
            }
            val target = TransformEngine.transform(
                userOriginal,
                config,
                allowRandomTail = forceTail,
                seqIndex = seqIndex,
                completeMessage = forceTail
            )
            if (target == current) {
                lastTarget = target
                diag("nochange len=${target.length}")
                return
            }
            val ok = writeBack(node, target)
            diag("rec: wrote=$ok ${current.length}->${target.length} r$round")
            if (!ok) return
            lastTarget = target
            if (!advanced) {
                seqIndex++
                advanced = true
            }
            if (sameAsTarget && !forceTail) return
            val fresh = try {
                if (node.refresh()) currentText(node) else null
            } catch (t: Throwable) {
                null
            }
            if (fresh == null || fresh == target) return
            lastObservedText = fresh
            current = fresh
        }
    }

    private fun writeBack(node: AccessibilityNodeInfo, target: String): Boolean {
        val setOk = setText(node, target)
        var verifiedLen = -1
        try {
            if (node.refresh()) {
                currentText(node)?.let { verifiedLen = it.length }
            }
        } catch (t: Throwable) {
        }
        val trusted = setOk && (verifiedLen < 0 || verifiedLen == target.length)
        if (trusted) return true
        val pasted = pasteViaClipboard(node, target)
        diag("write set=$setOk vlen=$verifiedLen paste=$pasted")
        return pasted
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
        node.text?.toString()
    } catch (t: Throwable) {
        null
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
