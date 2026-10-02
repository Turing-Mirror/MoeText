package com.turingmirror.moetext.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.engine.TransformEngine
import com.turingmirror.moetext.service.MoeAccessibilityService
import com.turingmirror.moetext.ui.theme.glassContentPadding

@Composable
private fun ServiceDiagPanel() {
    var lines by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) {
        while (true) {
            lines = MoeAccessibilityService.snapshotLogs()
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
            "在 QQ 或 Discord 中输入后可查看处理记录，反馈问题时可附上此页截图。",
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
internal fun TestTab(config: AppConfig) {
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
            "此处预览完整消息。聊天中的实际效果可在 QQ 或 Discord 会话中确认。",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
