package com.flowy.link.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowy.link.HostSession
import com.flowy.link.LinkStream
import com.flowy.link.LinkWait
import com.flowy.link.ModelChoice
import com.flowy.link.PendingConfirmation
import com.flowy.link.activateHost
import com.flowy.link.cacheConversations
import com.flowy.link.cachedConversations
import com.flowy.link.callHost
import com.flowy.link.clearSession
import com.flowy.link.confirmationLines
import com.flowy.link.conversationRows
import com.flowy.link.latestMessageId
import com.flowy.link.libraryRows
import com.flowy.link.loadDraft
import com.flowy.link.loadHosts
import com.flowy.link.loadLastConversation
import com.flowy.link.mergeDiscoveredAddress
import com.flowy.link.messageLines
import com.flowy.link.modelChoices
import com.flowy.link.saveDraft
import com.flowy.link.saveLastConversation
import com.flowy.link.saveSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener

private val reads = listOf(
    "模型" to "/api/providers",
    "技能" to "/api/skills",
    "知识库" to "/api/knowledge",
    "定时" to "/api/cron",
    "会议" to "/api/meetings",
    "文件" to "/api/fs",
    "设置" to "/api/settings",
)

private object Copy {
    const val empty = "这台电脑上还没有对话"
    const val start = "开始新对话"
    const val newer = "新对话"
    const val refresh = "刷新"
    const val retry = "重试"
    const val offline = "离线，只读"
    const val offlineBody = "离线，只读。连接恢复后可以继续。"
    const val offlineThread = "离线，只读。连上电脑后会显示这条对话。"
    const val message = "消息"
    const val sending = "正在发送"
    const val send = "发送"
    const val back = "返回"
    const val models = "模型"
    const val pending = "待确认"
    const val stop = "停止"
    const val modelTitle = "选择这台电脑上的模型"
    const val modelEmpty = "这台电脑还没有可用模型"
    const val backThread = "返回对话"
    const val confirmEmpty = "没有待确认的操作"
    const val confirmBody = "这些操作会在电脑上执行"
    const val allow = "允许"
    const val libraryTitle = "电脑上的内容"
    const val libraryEmpty = "这里还没有条目"
    const val computers = "电脑"
    const val add = "连接另一台电脑"
    const val wake = "锁屏提醒"
    const val wakeBody = "提醒只写状态，不包含对话正文。应用还在后台时会发系统通知。这一版没有登记推送令牌，进程结束后不会送到锁屏。"
    const val disconnect = "断开这台电脑"
    const val disconnectTitle = "断开这台电脑？"
    const val disconnectBody = "这台手机不再控制这台电脑。其他已配对的电脑还在。"
    const val cancel = "取消"
}

@Composable
fun HomeScreen(
    initial: HostSession,
    onDisconnect: () -> Unit,
    onAddComputer: () -> Unit,
    onSwitch: (HostSession) -> Unit,
) {
    val context = LocalContext.current
    var session by remember(initial.fingerprint) { mutableStateOf(initial) }
    var tab by remember { mutableStateOf("任务") }
    var offline by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        LinkWait.show = { notice = it }
        onDispose { LinkWait.show = null }
    }
    LaunchedEffect(initial.fingerprint) {
        val reachable = withContext(Dispatchers.IO) {
            runCatching {
                val discovered = mergeDiscoveredAddress(context, session)
                callHost(context, discovered, "GET", "/api/link/whoami")
                discovered
            }
        }
        reachable.onSuccess {
            session = it
            saveSession(context, it)
            notice = null
        }.onFailure { offline = true }
    }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth > 600.dp
        Column(Modifier.fillMaxSize().imePadding()) {
            if (notice != null) {
                Text(notice!!, color = Meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
            if (wide) {
                Row(Modifier.weight(1f)) {
                    TaskPane(session, offline, Modifier.weight(1.2f))
                    Box(Modifier.fillMaxHeight().width(1.dp).background(Line))
                    LibraryPane(session, offline, Modifier.weight(1f))
                    Box(Modifier.fillMaxHeight().width(1.dp).background(Line))
                    SettingsPane(session, offline, onSwitch, onAddComputer, {
                        clearSession(context)
                        onDisconnect()
                    }, { session = it }, Modifier.weight(0.9f))
                }
            } else {
                Box(Modifier.weight(1f)) {
                    TaskPane(session, offline, Modifier.pane(tab == "任务"))
                    LibraryPane(session, offline, Modifier.pane(tab == "库"))
                    SettingsPane(session, offline, onSwitch, onAddComputer, {
                        clearSession(context)
                        onDisconnect()
                    }, { session = it }, Modifier.pane(tab == "设置"))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Row {
                    listOf("任务", "库", "设置").forEach { item ->
                        TextButton(onClick = { tab = item }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text(item, color = if (tab == item) Attention else Meta)
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.pane(show: Boolean): Modifier = if (show) fillMaxSize() else size(0.dp)

@Composable
private fun TaskPane(session: HostSession, offline: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cached = remember(session.fingerprint) { cachedConversations(context, session.fingerprint) }
    var rows by remember(session.fingerprint) { mutableStateOf(cached) }
    var openId by remember(session.fingerprint) {
        mutableStateOf(loadLastConversation(context, session.fingerprint)?.takeIf { id -> cached.any { it.first == id } })
    }
    var messages by remember(session.fingerprint) { mutableStateOf(listOf<Pair<Boolean, String>>()) }
    var live by remember(session.fingerprint) { mutableStateOf("") }
    var draft by remember(session.fingerprint) {
        mutableStateOf(openId?.let { loadDraft(context, session.fingerprint, it) }.orEmpty())
    }
    var error by remember(session.fingerprint) { mutableStateOf<String?>(null) }
    var busy by remember(session.fingerprint) { mutableStateOf(false) }
    var loadingList by remember(session.fingerprint) { mutableStateOf(cached.isEmpty()) }
    var loadingMessages by remember(session.fingerprint) { mutableStateOf(false) }
    var showModels by remember(session.fingerprint) { mutableStateOf(false) }
    var modelLoading by remember(session.fingerprint) { mutableStateOf(false) }
    var models by remember(session.fingerprint) { mutableStateOf(listOf<ModelChoice>()) }
    var showConfirms by remember(session.fingerprint) { mutableStateOf(false) }
    var confirmLoading by remember(session.fingerprint) { mutableStateOf(false) }
    var confirms by remember(session.fingerprint) { mutableStateOf(listOf<PendingConfirmation>()) }
    val opened = openId
    val threadScroll = rememberScrollState()
    BackHandler(enabled = opened != null || showModels || showConfirms) {
        when {
            showModels -> showModels = false
            showConfirms -> showConfirms = false
            else -> {
                openId = null
                live = ""
            }
        }
    }
    if (opened != null) {
        DisposableEffect(opened) {
            val stream = LinkStream(context, session, opened, { live = it }, {
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { callHost(context, session, "GET", "/api/conversations/$opened/messages?limit=40") } }
                    result.onSuccess { messages = messageLines(it) }
                    live = ""
                }
            })
            if (!offline) stream.start()
            onDispose { stream.stop() }
        }
    }
    LaunchedEffect(opened, messages.size, live, threadScroll.maxValue) {
        threadScroll.scrollTo(threadScroll.maxValue)
    }
    suspend fun reload() {
        if (offline) {
            loadingList = false
            return
        }
        if (rows.isEmpty()) loadingList = true
        val result = withContext(Dispatchers.IO) { runCatching { callHost(context, session, "GET", "/api/conversations?limit=30") } }
        loadingList = false
        result.onSuccess {
            rows = conversationRows(it)
            cacheConversations(context, session.fingerprint, rows)
            error = null
        }.onFailure { error = it.message }
    }
    LaunchedEffect(session.fingerprint, offline) { reload() }
    LaunchedEffect(opened) {
        saveLastConversation(context, session.fingerprint, opened)
        draft = if (opened == null) "" else loadDraft(context, session.fingerprint, opened)
        if (opened == null || offline) return@LaunchedEffect
        loadingMessages = messages.isEmpty()
        val result = withContext(Dispatchers.IO) { runCatching { callHost(context, session, "GET", "/api/conversations/$opened/messages?limit=40") } }
        loadingMessages = false
        result.onSuccess { messages = messageLines(it) }
    }
    Column(modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(session.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (offline) Text(Copy.offline, color = Meta, style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).padding(top = 12.dp)) {
            when {
                showModels -> ModelList(models, modelLoading, offline, error, { choice ->
                    scope.launch {
                        val id = opened ?: return@launch
                        busy = true
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                callHost(context, session, "PATCH", "/api/conversations/$id", JSONObject().put("model", JSONObject().put("provider_id", choice.providerId).put("model", choice.model)).toString())
                            }
                        }
                        busy = false
                        result.onSuccess { showModels = false; error = null }.onFailure { error = it.message }
                    }
                }, { showModels = false })
                showConfirms -> ConfirmList(confirms, confirmLoading, offline, error, { item ->
                    scope.launch {
                        val id = opened ?: return@launch
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val history = callHost(context, session, "GET", "/api/conversations/$id/messages?limit=20")
                                val msgId = latestMessageId(history) ?: kotlin.error("没有可确认的消息")
                                val data = JSONTokener(item.option).nextValue()
                                callHost(context, session, "POST", "/api/conversations/$id/confirmations/${item.callId}/confirm", JSONObject().put("msg_id", msgId).put("data", data).toString())
                            }
                        }
                        result.onSuccess {
                            confirms = confirms.filter { row -> row.callId != item.callId }
                            error = null
                        }.onFailure { error = it.message }
                    }
                }, { showConfirms = false })
                opened == null -> ConversationList(rows, loadingList, offline, busy, error, { scope.launch { reload() } }, { openId = it }, {
                    if (busy) return@ConversationList
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { callHost(context, session, "POST", "/api/conversations", JSONObject().put("type", "nomi").put("name", "手机").put("extra", JSONObject()).toString()) }
                        }
                        busy = false
                        result.onSuccess {
                            val id = JSONObject(it).optJSONObject("data")?.optString("conversation_id").orEmpty()
                            if (id.isNotBlank()) {
                                rows = listOf(id to "手机") + rows.filter { row -> row.first != id }
                                cacheConversations(context, session.fingerprint, rows)
                                openId = id
                            }
                            error = null
                        }.onFailure { error = it.message }
                    }
                })
                else -> ThreadPane(messages, live, draft, offline, busy, loadingMessages, error, threadScroll, { next ->
                    draft = next
                    saveDraft(context, session.fingerprint, opened, next)
                }, {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                callHost(context, session, "POST", "/api/conversations/$opened/messages", JSONObject().put("content", draft).toString())
                                callHost(context, session, "GET", "/api/conversations/$opened/messages?limit=40")
                            }
                        }
                        busy = false
                        result.onSuccess {
                            messages = messageLines(it)
                            draft = ""
                            saveDraft(context, session.fingerprint, opened, "")
                            error = null
                        }.onFailure { error = it.message }
                    }
                }, {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { callHost(context, session, "POST", "/api/conversations/$opened/cancel") } }
                        result.onSuccess { error = null }.onFailure { error = it.message }
                    }
                }, {
                    showModels = true
                    modelLoading = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { callHost(context, session, "GET", "/api/providers") } }
                        modelLoading = false
                        result.onSuccess { models = modelChoices(it); error = null }.onFailure { error = it.message }
                    }
                }, {
                    showConfirms = true
                    confirmLoading = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { callHost(context, session, "GET", "/api/conversations/$opened/confirmations") } }
                        confirmLoading = false
                        result.onSuccess { confirms = confirmationLines(it); error = null }.onFailure { error = it.message }
                    }
                }, {
                    openId = null
                    live = ""
                })
            }
        }
    }
}

@Composable
private fun ConversationList(
    rows: List<Pair<String, String>>,
    loading: Boolean,
    offline: Boolean,
    busy: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        when {
            loading && rows.isEmpty() -> LoadingLines()
            rows.isEmpty() && offline -> Text(Copy.offlineBody, style = MaterialTheme.typography.bodyLarge)
            rows.isEmpty() && error != null -> {
                Text(error, color = Danger, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Attention, contentColor = androidx.compose.ui.graphics.Color.White)) {
                    Text(Copy.retry)
                }
            }
            rows.isEmpty() -> {
                Text(Copy.empty, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onCreate, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Attention, contentColor = androidx.compose.ui.graphics.Color.White)) {
                    Text(if (busy) Copy.sending else Copy.start)
                }
            }
            else -> {
                TextButton(onClick = onRefresh, enabled = !offline, modifier = Modifier.height(48.dp)) { Text(Copy.refresh) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    rows.forEach { (id, name) ->
                        RowItem(name) { onOpen(id) }
                    }
                }
                TextButton(onClick = onCreate, enabled = !offline && !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(if (busy) Copy.sending else Copy.newer) }
                if (error != null) Text(error, color = Danger, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ThreadPane(
    messages: List<Pair<Boolean, String>>,
    live: String,
    draft: String,
    offline: Boolean,
    busy: Boolean,
    loading: Boolean,
    error: String?,
    scroll: androidx.compose.foundation.ScrollState,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModels: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = onBack, modifier = Modifier.height(48.dp)) { Text(Copy.back) }
            TextButton(onClick = onModels, enabled = !offline, modifier = Modifier.height(48.dp)) { Text(Copy.models) }
            TextButton(onClick = onConfirm, enabled = !offline, modifier = Modifier.height(48.dp)) { Text(Copy.pending) }
            TextButton(onClick = onStop, enabled = !offline, modifier = Modifier.height(48.dp)) { Text(Copy.stop) }
        }
        Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading && messages.isEmpty()) {
                LoadingLines()
            } else if (offline && messages.isEmpty()) {
                Text(Copy.offlineThread, color = Meta, style = MaterialTheme.typography.bodySmall)
            }
            messages.forEach { (mine, text) -> Bubble(mine, text) }
            if (live.isNotBlank()) Bubble(false, live)
        }
        OutlinedTextField(draft, onDraft, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !offline, label = { Text(if (offline) Copy.offline else Copy.message) })
        Button(
            onClick = onSend,
            enabled = !offline && !busy && draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Attention, contentColor = androidx.compose.ui.graphics.Color.White),
        ) { Text(if (busy) Copy.sending else Copy.send) }
        if (error != null) Text(error, color = Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Bubble(mine: Boolean, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(
            text,
            modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(12.dp)).background(if (mine) Attention else Paper).padding(12.dp),
            color = if (mine) androidx.compose.ui.graphics.Color.White else Ink,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun LoadingLines() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(Paper))
        Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(Paper))
        Box(Modifier.fillMaxWidth(0.62f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(Paper))
    }
}

@Composable
private fun ModelList(models: List<ModelChoice>, loading: Boolean, offline: Boolean, error: String?, onPick: (ModelChoice) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(Copy.modelTitle, style = MaterialTheme.typography.bodyLarge)
        Box(Modifier.weight(1f).padding(top = 8.dp)) {
            when {
                loading -> LoadingLines()
                models.isEmpty() -> Text(Copy.modelEmpty, color = Meta, style = MaterialTheme.typography.bodySmall)
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    models.forEach { choice ->
                        RowItem("${choice.providerName} · ${choice.model}", enabled = !offline) { onPick(choice) }
                    }
                }
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.height(48.dp)) { Text(Copy.backThread) }
        if (error != null) Text(error, color = Danger, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ConfirmList(items: List<PendingConfirmation>, loading: Boolean, offline: Boolean, error: String?, onAllow: (PendingConfirmation) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            loading -> {
                Text(Copy.confirmBody, style = MaterialTheme.typography.bodyLarge)
                LoadingLines()
            }
            items.isEmpty() -> Text(Copy.confirmEmpty, style = MaterialTheme.typography.bodyLarge)
            else -> {
                Text(Copy.confirmBody, style = MaterialTheme.typography.bodyLarge)
                items.forEach { item ->
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { onAllow(item) }, enabled = !offline, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Attention, contentColor = androidx.compose.ui.graphics.Color.White)) {
                        Text(Copy.allow)
                    }
                }
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.height(48.dp)) { Text(Copy.backThread) }
        if (error != null) Text(error, color = Danger, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LibraryPane(session: HostSession, offline: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf<Pair<String, String>?>(null) }
    var rows by remember { mutableStateOf(listOf<String>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = section
    Column(modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        if (current == null) {
            Text(Copy.libraryTitle, style = MaterialTheme.typography.bodyLarge)
            Column(Modifier.weight(1f).padding(top = 8.dp).verticalScroll(rememberScrollState())) {
                reads.forEach { (label, path) ->
                    RowItem(label, enabled = !offline) {
                        section = label to path
                        loading = true
                        rows = emptyList()
                        error = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { callHost(context, session, "GET", path) } }
                            loading = false
                            result.onSuccess { rows = libraryRows(it) }.onFailure { error = it.message }
                        }
                    }
                }
            }
        } else {
            Text(current.first, style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.weight(1f).padding(top = 8.dp)) {
                when {
                    loading -> LoadingLines()
                    error != null && rows.isEmpty() -> Text(error!!, color = Danger, style = MaterialTheme.typography.bodySmall)
                    rows.isEmpty() -> Text(Copy.libraryEmpty, color = Meta, style = MaterialTheme.typography.bodyLarge)
                    else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        rows.forEach { NameRow(it) }
                    }
                }
            }
            TextButton(onClick = { section = null; error = null }, modifier = Modifier.height(48.dp)) { Text(Copy.back) }
            if (error != null && rows.isNotEmpty()) Text(error!!, color = Danger, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsPane(
    session: HostSession,
    offline: Boolean,
    onSwitch: (HostSession) -> Unit,
    onAdd: () -> Unit,
    onDisconnect: () -> Unit,
    onSession: (HostSession) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val hosts = loadHosts(context)
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(Copy.disconnectTitle, style = MaterialTheme.typography.bodyLarge) },
            text = { Text(Copy.disconnectBody, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    onDisconnect()
                }) { Text(Copy.disconnect, color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(Copy.cancel) } },
        )
    }
    Column(modifier.padding(horizontal = 24.dp, vertical = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Copy.computers, style = MaterialTheme.typography.bodyLarge)
        hosts.forEach { host ->
            RowItem(host.name, color = if (host.fingerprint == session.fingerprint) Attention else Ink) {
                activateHost(context, host.fingerprint)
                onSwitch(host)
            }
        }
        TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(Copy.add) }
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(Copy.wake, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = session.wakeEnabled, enabled = !offline, onCheckedChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { callHost(context, session, "POST", "/api/link/wake", JSONObject().put("enabled", enabled).toString()) } }
                    result.onSuccess {
                        val next = session.copy(wakeEnabled = enabled)
                        saveSession(context, next)
                        onSession(next)
                        error = null
                    }.onFailure { error = it.message }
                }
            })
        }
        Text(Copy.wakeBody, color = Meta, style = MaterialTheme.typography.bodySmall)
        Text(session.fingerprint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"))
        Button(onClick = { confirmDisconnect = true }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = androidx.compose.ui.graphics.Color.White)) {
            Text(Copy.disconnect)
        }
        if (error != null) Text(error!!, color = Danger, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RowItem(label: String, color: androidx.compose.ui.graphics.Color = Ink, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        NameRow(label, color)
    }
}

@Composable
private fun NameRow(label: String, color: androidx.compose.ui.graphics.Color = Ink) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterStart) {
            Text(label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}
