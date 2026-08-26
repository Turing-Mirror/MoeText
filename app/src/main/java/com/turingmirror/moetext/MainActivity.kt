package com.turingmirror.moetext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turingmirror.moetext.data.ConfigStore
import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.engine.CustomReplace
import com.turingmirror.moetext.engine.TransformEngine
import com.turingmirror.moetext.ui.theme.MoeTheme

private val NotifyAmber = Color(0xFFE8A91C)

class MainActivity : ComponentActivity() {

    private val serviceEnabled = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serviceEnabled.value = isServiceEnabled()
        setContent {
            MoeTheme {
                Root(
                    enabled = serviceEnabled.value,
                    onOpenAccessibility = { openAccessibilitySettings() },
                    initialConfig = ConfigStore.load(applicationContext),
                    onPersist = { ConfigStore.save(applicationContext, it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled.value = isServiceEnabled()
    }

    private fun isServiceEnabled(): Boolean = try {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        am?.getEnabledAccessibilityServiceList(-1)?.any {
            it.resolveInfo?.serviceInfo?.packageName == packageName
        } ?: false
    } catch (e: Exception) {
        false
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
    onOpenAccessibility: () -> Unit,
    initialConfig: AppConfig,
    onPersist: (AppConfig) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var config by remember { mutableStateOf(initialConfig) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(tab == 0, onClick = { tab = 0 }, icon = {}, label = { Text("状态") })
                NavigationBarItem(tab == 1, onClick = { tab = 1 }, icon = {}, label = { Text("规则") })
                NavigationBarItem(tab == 2, onClick = { tab = 2 }, icon = {}, label = { Text("测试") })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> StatusTab(enabled, config, onConfig = { config = it }, onOpenAccessibility)
                1 -> RulesTab(config, onConfig = { config = it }, onPersist = { onPersist(it) })
                2 -> TestTab(config)
            }
        }
    }
}

@Composable
private fun StatusTab(
    enabled: Boolean,
    config: AppConfig,
    onConfig: (AppConfig) -> Unit,
    onOpenAccessibility: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "MoeText",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "聊天文本自动美化",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .background(
                            color = if (enabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(5.dp)
                        )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (enabled) "无障碍服务已开启" else "无障碍服务未开启",
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onOpenAccessibility,
            enabled = !enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(if (enabled) "服务运行中" else "前往开启无障碍服务")
        }
        if (enabled) {
            Spacer(Modifier.height(6.dp))
            Text(
                "修改规则后无需重开服务，下次触发自动生效",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionTitle("处理模式")
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !config.realtimeMode,
                onClick = { onConfig(config.copy(realtimeMode = false)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("标点触发") }
            SegmentedButton(
                selected = config.realtimeMode,
                onClick = { onConfig(config.copy(realtimeMode = true)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("实时处理") }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "标点触发：仅在句读处立即处理（推荐）\n实时处理：每输入一字立即处理",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(22.dp))
        OutlinedCard(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = NotifyAmber.copy(alpha = 0.14f)),
            border = BorderStroke(1.dp, NotifyAmber.copy(alpha = 0.45f))
        ) {
            Text(
                "本工具会自动改写您发出的消息，可能违反目标应用的用户协议，存在账号被限制或封禁的风险，请自行评估后使用。MoeText 与腾讯官方无关。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp)
            )
        }

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
    var showAddDialog by remember { mutableStateOf(false) }
    var emoticonText by remember { mutableStateOf(config.emoticons.joinToString("\n")) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        SectionTitle("快捷替换")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            SwitchRow("我 → 本喵", config.woToBenmiao) { onConfig(config.copy(woToBenmiao = it)) }
            Spacer(Modifier.height(4.dp))
            SwitchRow("你 → 主人", config.niToZhuren) { onConfig(config.copy(niToZhuren = it)) }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("自定义替换")
        if (config.customReplaces.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PanelCard {
                config.customReplaces.forEachIndexed { index, rule ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${rule.from} → ${rule.to}",
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = rule.enabled, onCheckedChange = {
                            onConfig(config.copy(customReplaces = config.customReplaces.toMutableList().apply {
                                set(index, rule.copy(enabled = it))
                            }))
                        })
                        IconButton(onClick = {
                            onConfig(config.copy(customReplaces = config.customReplaces.filterIndexed { i, _ -> i != index }))
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
                value = config.sentenceSuffixText,
                onValueChange = { onConfig(config.copy(sentenceSuffixText = it)) },
                label = { Text("后缀文字") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("固定尾缀")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            SwitchRow("在整条消息末尾追加固定文字", config.tailEnabled) {
                onConfig(config.copy(tailEnabled = it))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = config.tailText,
                onValueChange = { onConfig(config.copy(tailText = it)) },
                label = { Text("尾缀内容") },
                placeholder = { Text("例如：哦齁齁齁❤️") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
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
                value = emoticonText,
                onValueChange = {
                    emoticonText = it
                    val list = it.split("\n").map { l -> l.trim() }.filter { l -> l.isNotEmpty() }
                    onConfig(config.copy(emoticons = list.ifEmpty { AppConfig.BUILTIN_EMOTICONS }))
                },
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
                onPersist(config)
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
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
                TransformEngine.transform(input, config, allowRandomTail = true),
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "预览即实际写回效果。随机颜文字每次抽取结果不同。",
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
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
