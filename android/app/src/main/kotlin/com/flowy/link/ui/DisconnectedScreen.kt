package com.flowy.link.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowy.link.pairWithHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object Copy {
    const val title = "连接这台电脑上的 Flowy"
    const val body = "在电脑的「手机版」里生成配对码，把链接粘贴到这里。手机会校验证书指纹，然后等你在电脑上点允许。"
    const val action = "连接这台电脑"
    const val field = "配对链接"
}

private val Danger = Color(0xFF8C3A3A)

@Composable
fun DisconnectedScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(Copy.title, fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(Copy.body, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Button(
            onClick = {
                busy = true
                error = null
                status = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { pairWithHost(context, url) }
                    }
                    busy = false
                    result.onSuccess { status = it.status }
                        .onFailure { error = it.message ?: "连接失败" }
                }
            },
            enabled = !busy && url.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(if (busy) "正在连接" else Copy.action)
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, fontSize = 13.sp, color = Danger)
        }
        if (status != null) {
            Spacer(Modifier.height(8.dp))
            Text(status!!, fontSize = 16.sp)
        }
    }
}
