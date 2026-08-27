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
    private var lastObservedText: String = ""
    @Volatile
    private var hasFreshInputHint: Boolean = false
    private var configDirty = true
    private var lastSendForceAt = 0L
    private var allowRandomNext = true

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
                    // 仅丢弃节点缓存与累积器；保留 lastObservedText 与 lastTarget，
                    // QQ 会高频抛 winState（面板开合等），全部清零会把自有证据抹掉
                    userOriginal = ""
                    seqIndex = 0
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
                    val incoming = evText?.takeIf { it.isNotBlank() } ?: before?.takeIf { it.isNotBlank() }
                    if (incoming != null) {
                        lastObservedText = incoming
                        hasFreshInputHint = true
                    } else {
                        hasFreshInputHint = false
                        diag("txt: 全部读空(疑似ROM脱敏)")
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
        lastObservedText = ""
        seqIndex = 0
        dropCachedInput()
        reloadConfig()
    }

    private fun requestWork(forceTail: Boolean) {
        if (workRunning) {
            workRequestedWhileRunning = true
            val grew = !queuedForceTail && forceTail
            queuedForceTail = queuedForceTail || forceTail
            diag("req: busy ft=$forceTail grew=$grew")
            return
        }
        if (workQueued) {
            val grew = !queuedForceTail && forceTail
            queuedForceTail = queuedForceTail || forceTail
            diag("req: queued ft=$forceTail grew=$grew")
            return
        }
        workQueued = true
        diag("req: new ft=$forceTail")
        val delay = if (!forceTail) REALTIME_DEBOUNCE_MS else 0L
        mainHandler.postDelayed({
            workQueued = false
            val ft = queuedForceTail.also { queuedForceTail = false }
            diag("exec: fire ft=$ft")
            runWorkCycle(forceTail = ft)
        }, delay)
    }

    private fun runWorkCycle(forceTail: Boolean) {
        if (workRunning) {
            diag("BUSY-DROP ft=$forceTail")
            return
        }
        workRunning = true
        try {
            if (configDirty) {
                reloadConfig()
                configDirty = false
            }
            val node = inputNode()
            var current: String? = try {
                node?.let { currentText(it) }
            } catch (t: Throwable) {
                null
            }
            if (current.isNullOrEmpty()) {
                val hint = lastObservedText.trim()
                if (hint.isNotEmpty()) {
                    current = hint
                    diag("cur: evtHint len=${hint.length}")
                }
            }
            if (current.isNullOrEmpty()) {
                diag("cur: empty (node=${node != null})")
                return
            }
            node?.let { reconcileNode(it, current, forceTail) }
        } catch (t: Throwable) {
            diag("cycle fail: ${t.javaClass.simpleName}")
        } finally {
            workRunning = false
            if (workRequestedWhileRunning) {
                workRequestedWhileRunning = false
                requestWork(forceTail = queuedForceTail.also { queuedForceTail = false })
            }
        }
    }

    private fun handlePossibleSend(event: AccessibilityEvent) {
        val source = try {
            event.source
        } catch (t: Throwable) {
            null
        }
        if (source == null) {
            val now = SystemClock.elapsedRealtime()
            allowRandomNext = now - lastSendForceAt > 2500
            lastSendForceAt = now
            diag("click src=null → treat as send emo=$allowRandomNext")
            requestWork(forceTail = true)
            return
        }
        val id = try {
            source.viewIdResourceName
        } catch (t: Throwable) {
            null
        }
        val isSend = id != null && (
            id.substringAfterLast('/').contains(SEND_ID_TOKEN, ignoreCase = true) ||
                id.contains("chat_msg_send", ignoreCase = true)
            )
        diag("click id=$id send=$isSend")
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
            val target = TransformEngine.transform(userOriginal, config, forceTail && allowRandomNext, seqIndex)
            allowRandomNext = true
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
        node.text?.toString()?.trim()
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
