package com.turingmirror.moetext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.turingmirror.moetext.engine.BidiTools
import com.turingmirror.moetext.ui.theme.glassContentPadding

@Composable
fun UnicodeTab() {
    var base by rememberSaveable { mutableStateOf("千早爱音") }
    var ending by rememberSaveable { mutableStateOf("喵～") }
    var pasted by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("这里填写消息内容。") }
    var strong by rememberSaveable { mutableStateOf(false) }
    var narrow by rememberSaveable { mutableStateOf(false) }
    var analysis by rememberSaveable { mutableStateOf(false) }
    val generated = remember(base, ending) { BidiTools.nickname(base, ending) }
    val target = pasted.ifEmpty { generated }
    val protected = remember(target, body, strong) { BidiTools.protect(target, body, strong) }
    val context = LocalContext.current
    fun copy(value: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MoeText", value))
        Toast.makeText(context, context.getString(R.string.unicode_copied), Toast.LENGTH_SHORT).show()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(glassContentPadding()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.unicode_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(base, { base = it.take(200) }, label = { Text(stringResource(R.string.unicode_base)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(ending, { ending = it.take(80) }, label = { Text(stringResource(R.string.unicode_ending)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row {
            TextButton({ copy(generated) }) { Text(stringResource(R.string.unicode_copy_nickname)) }
            TextButton({ copy(BidiTools.clean(base)) }) { Text(stringResource(R.string.unicode_copy_plain)) }
        }
        Text(stringResource(R.string.unicode_protection), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(pasted, { pasted = it.take(2000) }, label = { Text(stringResource(R.string.unicode_target)) }, modifier = Modifier.fillMaxWidth())
        TextButton({ copy(BidiTools.clean(target)) }) { Text(stringResource(R.string.unicode_copy_clean)) }
        OutlinedTextField(body, { body = it.take(8000) }, label = { Text(stringResource(R.string.unicode_body)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(strong, { strong = it })
            TextButton({ strong = !strong }) { Text(stringResource(R.string.unicode_strong)) }
        }
        Button({ copy(protected) }) { Text(stringResource(R.string.unicode_copy_protected)) }
        Text(stringResource(R.string.unicode_usage))
        TextButton({ narrow = !narrow }) { Text(stringResource(if (narrow) R.string.unicode_wide else R.string.unicode_narrow)) }
        val preview = if (narrow) Modifier.width(180.dp) else Modifier.fillMaxWidth()
        Text(stringResource(R.string.unicode_preview_nickname))
        Surface(preview, tonalElevation = 2.dp) { Text(generated, Modifier.padding(12.dp)) }
        Text(stringResource(R.string.unicode_preview_message))
        Surface(preview, tonalElevation = 2.dp) { Text("@$target $body", Modifier.padding(12.dp)) }
        Text(stringResource(R.string.unicode_preview_protected))
        Surface(preview, tonalElevation = 2.dp) { Text("@$target $protected", Modifier.padding(12.dp)) }
        Text(stringResource(R.string.unicode_preview_note))
        TextButton({ analysis = !analysis }) { Text(stringResource(R.string.unicode_analysis)) }
        if (analysis) {
            Text(BidiTools.inspect(target))
            Text(stringResource(R.string.unicode_counts, target.codePointCount(0, target.length), target.length))
            Text(BidiTools.inspect(protected))
        }
    }
}
