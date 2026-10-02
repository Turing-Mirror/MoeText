package com.turingmirror.moetext.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.turingmirror.moetext.ui.theme.GlassSegmentRow
import com.turingmirror.moetext.ui.theme.GlassSurface
import com.turingmirror.moetext.ui.theme.glassContentPadding
import com.turingmirror.moetext.update.UpdateChecker
import com.turingmirror.moetext.update.UpdateInfo

private const val KEY_AUTO_UPDATE = "auto_update"

private fun autoUpdateEnabled(context: Context): Boolean =
    context.getSharedPreferences(ConfigStore.PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_AUTO_UPDATE, true)

private fun setAutoUpdate(context: Context, value: Boolean) {
    context.getSharedPreferences(ConfigStore.PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_AUTO_UPDATE, value).apply()
}

@Composable
internal fun StatusTab(
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
            options = listOf("停顿处理", "实时处理"),
            selectedIndex = if (config.realtimeMode) 1 else 0,
            onSelected = { idx -> onConfig(config.copy(realtimeMode = idx == 1)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "支持 QQ 和 Discord 聊天输入框。停顿处理：输入时替换文字，短暂停顿后补齐后缀。实时处理：文字确认输入后同步补齐后缀。修改文字后会继续处理，同一条消息的后缀选择保持不变。",
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
private fun AboutPanel() {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf("当前版本 v" + UpdateChecker.currentVersionName(context))
    }
    var offer by remember { mutableStateOf<UpdateInfo?>(null) }
    var auto by remember { mutableStateOf(autoUpdateEnabled(context)) }
    var autoChecked by rememberSaveable { mutableStateOf(false) }

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
        // 页签切换会重建本面板，自动检查每会话只做一次。
        if (auto && !autoChecked) {
            autoChecked = true
            performCheck()
        }
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
