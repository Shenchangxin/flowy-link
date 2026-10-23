package com.flowy.link.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flowy.link.HostSession
import com.flowy.link.cachedConversations
import com.flowy.link.callHost
import com.flowy.link.clearSession
import com.flowy.link.mergeDiscoveredAddress
import com.flowy.link.saveSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Danger = Color(0xFF8C3A3A)

private val libraryReads = listOf(
    "模型" to "/api/providers",
    "技能" to "/api/skills",
    "知识库" to "/api/knowledge",
    "定时" to "/api/cron",
    "会议" to "/api/meetings",
    "文件" to "/api/fs",
    "设置" to "/api/settings",
)

@Composable
fun HomeScreen(initial: HostSession, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    var session by remember { mutableStateOf(initial) }
    var tab by remember { mutableStateOf("任务") }
    var offline by remember { mutableStateOf(false) }
    LaunchedEffect(initial.fingerprint) {
        val reachable = withContext(Dispatchers.IO) {
            runCatching {
                val discovered = mergeDiscoveredAddress(context, session)
                callHost(context, discovered, "GET", "/api/link/whoami")
                discovered
            }
        }
        reachable.onSuccess { session = it }
            .onFailure { offline = true }
    }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth > 600.dp
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        TaskPane(session, offline, Modifier.weight(1.2f).padding(24.dp))
                        LibraryPane(session, offline, Modifier.weight(1f).padding(24.dp))
                        SettingsPane(session, offline, {
                            session = it
                            saveSession(context, it)
                        }, {
                            clearSession(context)
                            onDisconnect()
                        }, Modifier.weight(0.8f).padding(24.dp))
                    }
                } else {
                    when (tab) {
                        "任务" -> TaskPane(session, offline, Modifier.fillMaxSize().padding(24.dp))
                        "库" -> LibraryPane(session, offline, Modifier.fillMaxSize().padding(24.dp))
                        else -> SettingsPane(session, offline, {
                            session = it
                            saveSession(context, it)
                        }, {
                            clearSession(context)
                            onDisconnect()
                        }, Modifier.fillMaxSize().padding(24.dp))
                    }
                }
            }
            if (!wide) {
                NavigationBar {
                    listOf("任务", "库", "设置").forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Text(item) },
                            label = { Text(item) },
                        )
                    }
                }
            }
        }
    }
}

private data class ConversationRow(val id: String, val name: String)

@Composable
private fun TaskPane(session: HostSession, offline: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(cachedConversations(context).map { ConversationRow("", it) }) }
    var draft by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf<String?>(null) }
    var approval by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (offline) "${session.name} · 离线，只读" else session.name)
        Button(
            onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { callHost(context, session, "GET", "/api/conversations?limit=30") }
                    }
                    busy = false
                    result.onSuccess { text ->
                        rows = conversationRows(text)
                        cacheNames(context, rows.map { it.name })
                        error = null
                    }.onFailure { error = it.message }
                }
            },
            enabled = !offline && !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("刷新对话") }
        rows.forEach { row ->
            Text(row.name, modifier = Modifier.fillMaxWidth().clickable(enabled = row.id.isNotEmpty()) { selected = row.id })
        }
        OutlinedTextField(draft, { draft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("新消息") }, enabled = !offline)
        Button(
            onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val created = callHost(
                                context,
                                session,
                                "POST",
                                "/api/conversations",
                                JSONObject().put("type", "nomi").put("name", "手机").put("extra", JSONObject()).toString(),
                            )
                            val id = JSONObject(created).optJSONObject("data")?.optString("conversation_id").orEmpty()
                            callHost(
                                context,
                                session,
                                "POST",
                                "/api/conversations/$id/messages",
                                JSONObject().put("content", draft).toString(),
                            )
                            val messages = callHost(context, session, "GET", "/api/conversations/$id/messages?limit=20")
                            id to lastMessageText(messages)
                        }
                    }
                    busy = false
                    result.onSuccess { (id, text) ->
                        selected = id
                        reply = text
                        draft = ""
                        error = null
                    }.onFailure { error = it.message }
                }
            },
            enabled = !offline && !busy && draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("发给这台电脑") }
        Button(
            onClick = {
                val id = selected ?: return@Button
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { callHost(context, session, "POST", "/api/conversations/$id/cancel") }
                    }
                    result.onFailure { error = it.message }.onSuccess { error = null }
                }
            },
            enabled = !offline && selected != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("停止当前对话") }
        Button(
            onClick = {
                val id = selected ?: return@Button
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { callHost(context, session, "GET", "/api/conversations/$id/confirmations") }
                    }
                    result.onSuccess { approval = it.take(500) }.onFailure { error = it.message }
                }
            },
            enabled = !offline && selected != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("查看待确认") }
        if (selected != null) Text("当前对话 $selected")
        if (reply != null) Text(reply!!)
        if (approval != null) Text(approval!!)
        if (error != null) Text(error!!, color = Danger)
    }
}

@Composable
private fun LibraryPane(session: HostSession, offline: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("这些读取发生在电脑上")
        libraryReads.forEach { (label, path) ->
            Button(
                onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { callHost(context, session, "GET", path) }
                        }
                        result.onSuccess { body = "$label\n${it.take(500)}" }
                            .onFailure { error = it.message }
                    }
                },
                enabled = !offline,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(label) }
        }
        if (body.isNotEmpty()) Text(body)
        if (error != null) Text(error!!, color = Danger)
    }
}

@Composable
private fun SettingsPane(
    session: HostSession,
    offline: Boolean,
    onSession: (HostSession) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(session.name)
        Text(session.fingerprint)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("锁屏唤醒")
            Switch(
                checked = session.wakeEnabled,
                enabled = !offline,
                onCheckedChange = { enabled ->
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                callHost(context, session, "POST", "/api/link/wake", JSONObject().put("enabled", enabled).toString())
                            }
                        }
                        result.onSuccess { onSession(session.copy(wakeEnabled = enabled)) }
                            .onFailure { error = it.message }
                    }
                },
            )
        }
        Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("断开这台电脑") }
        if (error != null) Text(error!!, color = Danger)
    }
}

private fun conversationRows(text: String): List<ConversationRow> {
    val data = JSONObject(text).optJSONObject("data") ?: JSONObject(text)
    val items = data.optJSONArray("items") ?: JSONArray()
    return List(items.length()) { index ->
        val item = items.getJSONObject(index)
        val id = item.optString("conversation_id")
        ConversationRow(id, item.optString("name").ifBlank { id })
    }
}

private fun lastMessageText(text: String): String {
    val data = JSONObject(text).optJSONObject("data") ?: JSONObject(text)
    val items = data.optJSONArray("items") ?: JSONArray()
    if (items.length() == 0) return ""
    val content = items.getJSONObject(items.length() - 1).opt("content")
    return content?.toString().orEmpty().take(500)
}

private fun cacheNames(context: android.content.Context, names: List<String>) {
    val array = JSONArray()
    names.forEach { array.put(it) }
    context.getSharedPreferences("flowy-link", android.content.Context.MODE_PRIVATE)
        .edit().putString("conversation-cache", array.toString()).apply()
}
