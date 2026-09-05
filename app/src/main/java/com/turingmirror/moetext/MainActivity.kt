package com.turingmirror.moetext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import com.turingmirror.moetext.ui.theme.GlassBottomBar
import com.turingmirror.moetext.ui.theme.GlassSegmentRow
import com.turingmirror.moetext.ui.theme.GlassSurface
import com.turingmirror.moetext.ui.theme.GlassSwitch
import com.turingmirror.moetext.ui.theme.glassBackdrop
import com.turingmirror.moetext.ui.theme.glassContentPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turingmirror.moetext.data.ConfigStore
import com.turingmirror.moetext.data.readLimitedText
import com.turingmirror.moetext.data.PresetCodec
import com.turingmirror.moetext.data.StylePresets
import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.engine.CustomReplace
import com.turingmirror.moetext.engine.PickMode
import com.turingmirror.moetext.engine.TransformEngine
import com.turingmirror.moetext.update.UpdateChecker
import com.turingmirror.moetext.update.UpdateInfo
import com.turingmirror.moetext.ui.theme.MoeTheme
import dev.chrisbanes.haze.rememberHazeState

private const val PREFS_NAME = "moetext_config"
private const val KEY_AUTO_UPDATE = "auto_update"
private const val REPLACE_PAGE_SIZE = 20

private fun autoUpdateEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_AUTO_UPDATE, true)

private fun setAutoUpdate(context: Context, value: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_AUTO_UPDATE, value).apply()
}

private val NotifyAmber = Color(0xFFE8A91C)

class MainActivity : ComponentActivity() {

    private val serviceEnabled = mutableStateOf(false)
    private val serviceConnected = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshServiceState()
        setContent {
            MoeTheme {
                Root(
                    enabled = serviceEnabled.value,
                    connected = serviceConnected.value,
                    onOpenAccessibility = { openAccessibilitySettings() },
                    onRequestBattery = { requestIgnoreBatteryOptimization() },
                    initialConfig = ConfigStore.load(applicationContext),
                    onPersist = { ConfigStore.save(applicationContext, it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshServiceState()
    }

    private fun refreshServiceState() {
        serviceEnabled.value = try {
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )?.contains("$packageName/") == true
        } catch (e: Exception) {
            false
        }
        serviceConnected.value = runCatching { connectedServiceRunning() }.getOrDefault(false)
    }

    private fun connectedServiceRunning(): Boolean {
        return com.turingmirror.moetext.service.MoeAccessibilityService.connected
    }

    private fun requestIgnoreBatteryOptimization() {
        val intents = listOf(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            ),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (ignored: Exception) {
            }
        }
        Toast.makeText(this, "无法打开电池设置", Toast.LENGTH_SHORT).show()
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun Root(
    enabled: Boolean,
    connected: Boolean,
    onOpenAccessibility: () -> Unit,
    onRequestBattery: () -> Unit,
    initialConfig: AppConfig,
    onPersist: (AppConfig) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var config by remember { mutableStateOf(initialConfig) }
    val tabStates = rememberSaveableStateHolder()

    val hazeState = rememberHazeState()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(Modifier.fillMaxSize().glassBackdrop(hazeState)) {
            tabStates.SaveableStateProvider(tab) {
            when (tab) {
                0 -> StatusTab(
                    enabled,
                    connected,
                    config,
                    onConfig = {
                        config = it
                        onPersist(it)
                    },
                    onOpenAccessibility,
                    onRequestBattery
                )
                1 -> RulesTab(config, onConfig = { config = it }, onPersist = { onPersist(it) })
                2 -> TestTab(config)
                3 -> UnicodeTab()
            }
            }
        }
        GlassBottomBar(
            selected = tab,
            onSelect = { tab = it },
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun StatusTab(
    enabled: Boolean,
    connected: Boolean,
    config: AppConfig,
    onConfig: (AppConfig) -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestBattery: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(glassContentPadding())
    ) {
        Column {
            Text(
                "喵言喵",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "MoeText by Turing Mirror",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(18.dp))

        GlassSurface(
            modifier = Modifier.fillMaxWidth().clickable { onOpenAccessibility() },
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    when {
                        !enabled -> "无障碍服务未开启"
                        connected -> "无障碍服务已连接"
                        else -> "服务未连接，请重新开关一次"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.error
                        connected -> Color(0xFF2E7D32)
                        else -> NotifyAmber
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (enabled) {
                        if (connected) "请在系统设置中关闭无障碍服务以停止处理"
                        else "服务尚未连接，请重新开启或查看兼容性指引"
                    } else "点击前往开启无障碍服务",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        CompatPanel(onRequestBattery)

        Spacer(Modifier.height(18.dp))
        SectionTitle("处理模式")
        Spacer(Modifier.height(8.dp))
        GlassSegmentRow(
            options = listOf("发送触发", "实时处理"),
            selectedIndex = if (config.realtimeMode) 1 else 0,
            onSelected = { idx -> onConfig(config.copy(realtimeMode = idx == 1)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "发送触发：句末标点可提前触发处理；发送点击仅作补充，不能保证赶在 QQ 发出消息前完成。\n实时处理：输入过程中替换文字，为已结束的句子追加后缀。\n手动修改已转换的文字后，本条消息暂停自动处理，清空输入框后恢复。",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(22.dp))
        Text(
            "本工具会自动改写您发出的消息，可能违反目标应用的用户协议，存在账号被限制或封禁的风险，请自行评估后使用。喵言喵与腾讯官方无关。",
            fontSize = 12.sp,
            color = NotifyAmber,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(22.dp))
        SectionTitle("社区")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            LinkRow("GitHub 仓库", "https://github.com/Turing-Mirror/MoeText")
            LinkRow("哔哩哔哩 @图灵镜", "https://space.bilibili.com/3546871148579062")
            LinkRow("抖音 @图灵镜", "https://v.douyin.com/6NxXcrKK9cc")
            LinkRow("小红书 @图灵镜", "https://www.xiaohongshu.com/user/profile/65f56bf1000000000b00e094")
            QQGroupRow()
        }

        Spacer(Modifier.height(22.dp))
        SectionTitle("关于")
        Spacer(Modifier.height(8.dp))
        AboutPanel()
    }
}

@Composable
private fun CompatPanel(onRequestBattery: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    PanelCard {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "机型兼容性指引",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
        Text(
            "若无障碍开关被自动关闭或服务频繁掉线，请按品牌逐一检查：",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "vivo / iQOO（OriginOS）\n· 设置 → 电池 → 后台高耗电：允许喵言喵\n· 最近任务中下拉卡片锁定本应用\n· 关闭「睡眠待机优化」对后台的限制\n\nOPPO / OnePlus（ColorOS）\n· 设置 → 应用 → 喵言喵 → 允许自启动与关联启动\n· 耗电管理改为「允许完全后台行为」\n· 勿在最近任务中清理本应用\n\n华为 / 荣耀（鸿蒙 / MagicOS）\n· 设置 → 应用 → 启动管理：手动管理，三项全开\n\n小米（HyperOS）\n· 省电策略设为「无限制」，开启「后台弹出」",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onRequestBattery) {
            Text("申请忽略电池优化", fontSize = 13.sp)
        }
        }
    }
}

@Composable
private fun ServiceDiagPanel() {
    var lines by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) {
        while (true) {
            lines = com.turingmirror.moetext.service.MoeAccessibilityService.snapshotLogs()
            kotlinx.coroutines.delay(500)
        }
    }
    PanelCard {
        Text(
            "服务诊断",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "在 QQ 中输入后日志应持续新增；把此截图发给开发者可精确定位问题。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (lines.isEmpty()) {
                Text("（暂无日志）", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                lines.asReversed().forEach { line ->
                    Text(line, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun AboutPanel() {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf("当前版本 v" + UpdateChecker.currentVersionName(context))
    }
    var offer by remember { mutableStateOf<UpdateInfo?>(null) }
    var auto by remember { mutableStateOf(autoUpdateEnabled(context)) }

    fun performCheck() {
        busy = true
        status = "检查中…"
        UpdateChecker.check(context) { result ->
            busy = false
            result.fold(
                onSuccess = { info ->
                    if (info == null) status = "已是最新版本"
                    else {
                        status = "发现新版本 v${info.versionName}"
                        offer = info
                    }
                },
                onFailure = { status = "检查失败（网络异常）" }
            )
        }
    }

    LaunchedEffect(Unit) {
        if (auto) performCheck()
    }

    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "喵言喵 v" + UpdateChecker.currentVersionName(context),
                    fontSize = 14.sp
                )
                Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { performCheck() }, enabled = !busy) {
                Text(if (busy) "检查中…" else "检查更新")
            }
        }
        SwitchRow("启动时自动检查更新", auto) {
            setAutoUpdate(context, it)
            auto = it
        }
    }

    offer?.let { info ->
        AlertDialog(
            onDismissRequest = { offer = null },
            title = { Text("发现新版本 v${info.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (info.changelog.isEmpty()) {
                        Text("性能优化与问题修复。")
                    } else {
                        info.changelog.forEach { line -> Text("· $line", fontSize = 13.sp) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    openUrl(context, info.releasePage)
                    offer = null
                }) { Text("前往下载") }
            },
            dismissButton = {
                TextButton(onClick = { offer = null }) { Text("以后再说") }
            }
        )
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun LinkRow(label: String, url: String) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { openUrl(context, url) }) { Text("前往") }
    }
}

@Composable
private fun QQGroupRow() {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("QQ 群 @图灵镜社区（1077458748）", fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("qq_group", "1077458748"))
            Toast.makeText(context, "群号已复制", Toast.LENGTH_SHORT).show()
        }) { Text("复制群号") }
    }
}

@Composable
private fun RulesTab(config: AppConfig, onConfig: (AppConfig) -> Unit, onPersist: (AppConfig) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    fun lines(raw: String) = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    var suffixRaw by rememberSaveable { mutableStateOf(config.sentenceSuffixes.joinToString("\n")) }
    var tailRaw by rememberSaveable { mutableStateOf(config.tails.joinToString("\n")) }
    var emoticonRaw by rememberSaveable { mutableStateOf(config.emoticons.joinToString("\n")) }

    fun merged(): AppConfig = config.copy(
        sentenceSuffixes = lines(suffixRaw).ifEmpty { listOf("喵") },
        tails = lines(tailRaw),
        emoticons = lines(emoticonRaw).ifEmpty { AppConfig.BUILTIN_EMOTICONS }
    )

    val draft = merged()
    val selectedStyle = remember(draft) {
        StylePresets.BUILTIN.find { StylePresets.apply(draft, it.config) == draft }?.name ?: "自定义"
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = PresetCodec.toJson(merged())
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(json.toByteArray())
                        } ?: error("无法写入文件")
                    }
                    Toast.makeText(context, "风格已导出", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                            it.readLimitedText(262144)
                        } ?: error("无法读取文件")
                        PresetCodec.parse(text) ?: error("格式错误")
                    }
                }.onSuccess { parsed ->
                    val applied = StylePresets.apply(merged(), parsed)
                    suffixRaw = applied.sentenceSuffixes.joinToString("\n")
                    tailRaw = applied.tails.joinToString("\n")
                    emoticonRaw = applied.emoticons.joinToString("\n")
                    onConfig(applied)
                    onPersist(applied)
                    Toast.makeText(context, "风格已导入并保存", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "导入失败：文件无效、过大或无法读取", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun applyStyle(presetConfig: AppConfig, name: String) {
        val applied = StylePresets.apply(merged(), presetConfig)
        suffixRaw = applied.sentenceSuffixes.joinToString("\n")
        tailRaw = applied.tails.joinToString("\n")
        emoticonRaw = applied.emoticons.joinToString("\n")
        onConfig(applied)
        onPersist(applied)
        Toast.makeText(context, "已切换到「$name」", Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(glassContentPadding())
    ) {
        SectionTitle("风格")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StylePresets.BUILTIN.forEach { p ->
                    FilterChip(
                        selected = selectedStyle == p.name,
                        onClick = { applyStyle(p.config, p.name) },
                        label = { Text(p.name) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { exportLauncher.launch("moetext_style.json") }) {
                    Text("导出当前风格", fontSize = 13.sp)
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                    Text("导入风格", fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("快捷替换")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            SwitchRow("我 → 本喵", config.woToBenmiao) { onConfig(config.copy(woToBenmiao = it)) }
            Spacer(Modifier.height(4.dp))
            SwitchRow("我们 → 本喵们", config.woMenToBenmiaoMen) { onConfig(config.copy(woMenToBenmiaoMen = it)) }
            Spacer(Modifier.height(4.dp))
            SwitchRow("你们 → 主人们", config.niMenToZhurenMen) { onConfig(config.copy(niMenToZhurenMen = it)) }
            Spacer(Modifier.height(4.dp))
            SwitchRow("你 → 主人", config.niToZhuren) { onConfig(config.copy(niToZhuren = it)) }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("自定义替换")
        val replaces = config.customReplaces
        val totalPages = maxOf(1, (replaces.size + REPLACE_PAGE_SIZE - 1) / REPLACE_PAGE_SIZE)
        var replacePage by rememberSaveable { mutableIntStateOf(0) }
        LaunchedEffect(replaces.size) {
            if (replacePage >= totalPages) replacePage = totalPages - 1
        }
        val pageStart = replacePage * REPLACE_PAGE_SIZE
        val pageItems = replaces.drop(pageStart).take(REPLACE_PAGE_SIZE)
        if (pageItems.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PanelCard {
                Text(
                    "共 ${replaces.size} 条 · 第 ${replacePage + 1} / $totalPages 页",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                pageItems.forEachIndexed { i, rule ->
                    val index = pageStart + i
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${rule.from} → ${rule.to}",
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        GlassSwitch(checked = rule.enabled, onCheckedChange = {
                            onConfig(config.copy(customReplaces = config.customReplaces.toMutableList().apply {
                                set(index, rule.copy(enabled = it))
                            }))
                        })
                        IconButton(onClick = {
                            onConfig(config.copy(customReplaces = config.customReplaces.filterIndexed { idx, _ -> idx != index }))
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (totalPages > 1) {
                var jumpRaw by remember { mutableStateOf("") }
                Spacer(Modifier.height(4.dp))
                PanelCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { replacePage-- },
                            enabled = replacePage > 0
                        ) { Text("上一页") }
                        Text(
                            "${replacePage + 1} / $totalPages",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        TextButton(
                            onClick = { replacePage++ },
                            enabled = replacePage < totalPages - 1
                        ) { Text("下一页") }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = jumpRaw,
                            onValueChange = { jumpRaw = it.filter { c -> c.isDigit() } },
                            label = { Text("页码直达") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.width(120.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val n = jumpRaw.toIntOrNull()
                            if (n != null) {
                                replacePage = (n - 1).coerceIn(0, totalPages - 1)
                                jumpRaw = ""
                            }
                        }) { Text("前往") }
                    }
                }
            }
        }
        TextButton(onClick = { showAddDialog = true }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("添加替换规则")
        }

        Spacer(Modifier.height(8.dp))
        SectionTitle("句尾后缀")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            SwitchRow("在每句末尾追加后缀", config.sentenceSuffixEnabled) {
                onConfig(config.copy(sentenceSuffixEnabled = it))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = suffixRaw,
                onValueChange = { suffixRaw = it },
                label = { Text("后缀库（每行一个，顺序轮换或随机抽取）") },
                placeholder = { Text("喵\n nya~") },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            PickModeRow(config.sentenceSuffixPick) {
                onConfig(merged().copy(sentenceSuffixPick = it))
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("固定尾缀")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            SwitchRow("在整条消息末尾追加尾缀", config.tailEnabled) {
                onConfig(config.copy(tailEnabled = it))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = tailRaw,
                onValueChange = { tailRaw = it },
                label = { Text("尾缀库（每行一个，顺序轮换或随机抽取）") },
                placeholder = { Text("哦齁齁齁❤️\n 喵呜～") },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            PickModeRow(config.tailPick) {
                onConfig(merged().copy(tailPick = it))
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("随机颜文字")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            SwitchRow("在消息末尾追加随机颜文字", config.emoticonEnabled) {
                onConfig(config.copy(emoticonEnabled = it))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = emoticonRaw,
                onValueChange = { emoticonRaw = it },
                label = { Text("颜文字库（每行一个，留空使用内置库）") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                val finalConfig = merged()
                onConfig(finalConfig)
                onPersist(finalConfig)
                Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
            },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存设置")
        }
        Spacer(Modifier.height(30.dp))

        if (showAddDialog) {
            AddReplaceDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { from, to ->
                    onConfig(config.copy(customReplaces = config.customReplaces + CustomReplace(true, from, to)))
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
private fun PickModeRow(current: PickMode, onChange: (PickMode) -> Unit) {
    GlassSegmentRow(
        options = listOf("顺序轮换", "随机抽取"),
        selectedIndex = if (current == PickMode.SEQUENTIAL) 0 else 1,
        onSelected = { idx -> onChange(if (idx == 0) PickMode.SEQUENTIAL else PickMode.RANDOM) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AddReplaceDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加替换规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text("原文") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("替换为") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (from.isNotEmpty()) onConfirm(from, to) },
                enabled = from.isNotEmpty()
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun TestTab(config: AppConfig) {
    var input by rememberSaveable { mutableStateOf("今天我很好，你准备好了吗？我们去公园玩吧。") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(glassContentPadding())
    ) {
        SectionTitle("服务诊断")
        Spacer(Modifier.height(8.dp))
        ServiceDiagPanel()
        Spacer(Modifier.height(22.dp))
        SectionTitle("实时预览")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("输入原文") },
            minLines = 3,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        PanelCard {
            Text("处理后", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                remember(input, config) { TransformEngine.transform(input, config, allowRandomTail = true) },
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "此处预览完整消息。QQ 输入过程与发送时的效果需要在会话中确认。",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        GlassSwitch(checked = checked, onCheckedChange = onChange)
    }
}
