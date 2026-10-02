package com.turingmirror.moetext.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turingmirror.moetext.data.PresetCodec
import com.turingmirror.moetext.data.StylePresets
import com.turingmirror.moetext.data.readLimitedText
import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.engine.CustomReplace
import com.turingmirror.moetext.engine.PickMode
import com.turingmirror.moetext.ui.theme.GlassSegmentRow
import com.turingmirror.moetext.ui.theme.GlassSwitch
import com.turingmirror.moetext.ui.theme.glassContentPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REPLACE_PAGE_SIZE = 20

@Composable
internal fun RulesTab(config: AppConfig, onConfig: (AppConfig) -> Unit, onPersist: (AppConfig) -> Unit) {
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

    // 所有改动即时生效：无障碍服务读的是 SharedPreferences，
    // 只更新内存状态会让「已拨过去的开关」实际上没有作用。
    fun update(c: AppConfig) {
        onConfig(c)
        onPersist(c)
    }

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
                    update(applied)
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
        update(applied)
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
            SwitchRow("我 → 本喵", config.woToBenmiao) { update(config.copy(woToBenmiao = it)) }
            Spacer(Modifier.height(4.dp))
            SwitchRow("我们 → 本喵们", config.woMenToBenmiaoMen) { update(config.copy(woMenToBenmiaoMen = it)) }
            Spacer(Modifier.height(4.dp))
            SwitchRow("你们 → 主人们", config.niMenToZhurenMen) { update(config.copy(niMenToZhurenMen = it)) }
            Spacer(Modifier.height(4.dp))
            SwitchRow("你 → 主人", config.niToZhuren) { update(config.copy(niToZhuren = it)) }
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
                            update(config.copy(customReplaces = config.customReplaces.toMutableList().apply {
                                set(index, rule.copy(enabled = it))
                            }))
                        })
                        IconButton(onClick = {
                            update(config.copy(customReplaces = config.customReplaces.filterIndexed { idx, _ -> idx != index }))
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
                update(config.copy(sentenceSuffixEnabled = it))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = suffixRaw,
                onValueChange = { suffixRaw = it; update(merged()) },
                label = { Text("后缀库（每行一个，顺序轮换或随机抽取）") },
                placeholder = { Text("喵\n nya~") },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            PickModeRow(config.sentenceSuffixPick) {
                update(merged().copy(sentenceSuffixPick = it))
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("固定尾缀")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            SwitchRow("在整条消息末尾追加尾缀", config.tailEnabled) {
                update(config.copy(tailEnabled = it))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = tailRaw,
                onValueChange = { tailRaw = it; update(merged()) },
                label = { Text("尾缀库（每行一个，顺序轮换或随机抽取）") },
                placeholder = { Text("哦齁齁齁❤️\n 喵呜～") },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            PickModeRow(config.tailPick) {
                update(merged().copy(tailPick = it))
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("随机颜文字")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            SwitchRow("在消息末尾追加随机颜文字", config.emoticonEnabled) {
                update(config.copy(emoticonEnabled = it))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = emoticonRaw,
                onValueChange = { emoticonRaw = it; update(merged()) },
                label = { Text("颜文字库（每行一个，留空使用内置库）") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(22.dp))

        if (showAddDialog) {
            AddReplaceDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { from, to ->
                    update(config.copy(customReplaces = config.customReplaces + CustomReplace(true, from, to)))
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
