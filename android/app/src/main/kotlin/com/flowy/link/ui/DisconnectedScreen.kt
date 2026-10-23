package com.flowy.link.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flowy.link.pairWithHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object Copy {
    const val title = "连接这台电脑上的 Flowy"
    const val body = "在电脑的「手机版」里生成配对码。扫码或粘贴后，手机会核对证书指纹，并等你在电脑上点允许。"
    const val connect = "连接这台电脑"
    const val scan = "扫描配对码"
    const val field = "配对链接"
    const val cancel = "返回已连接的电脑"
}

@Composable
fun DisconnectedScreen(onConnected: () -> Unit, onCancel: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    fun connect(raw: String) {
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { pairWithHost(context, raw) } }
            busy = false
            result.onSuccess { if (it.session.accessToken.isNotBlank()) onConnected() }
                .onFailure { error = it.message ?: "连接失败" }
        }
    }
    if (scanning) {
        BackHandler { scanning = false }
        ScanScreen(onCode = {
            url = it
            scanning = false
            connect(it)
        }, onClose = {
            scanning = false
            if (it != null) error = it
        })
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(Copy.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(Copy.body, style = MaterialTheme.typography.bodyLarge, color = Meta)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(Copy.field) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            minLines = 3,
        )
        Spacer(Modifier.height(16.dp))
        val filled = url.isNotBlank()
        Button(
            onClick = {
                if (!filled) {
                    error = null
                    scanning = true
                    return@Button
                }
                busy = true
                error = null
                connect(url)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Attention, contentColor = androidx.compose.ui.graphics.Color.White),
        ) { Text(if (busy) "正在连接" else if (filled) Copy.connect else Copy.scan) }
        if (!filled) {
            Text("也可以把链接直接粘贴到上面的输入框。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        if (error != null) {
            Text(error!!, color = Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(Copy.cancel) }
        }
    }
}
