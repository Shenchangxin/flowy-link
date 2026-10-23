package com.flowy.link

import org.json.JSONArray
import org.json.JSONObject

data class ModelChoice(val providerId: String, val providerName: String, val model: String)

data class PendingConfirmation(val callId: String, val title: String, val option: String)

fun displayText(value: Any?): String {
    return when (value) {
        null, JSONObject.NULL -> ""
        is String -> value
        is JSONArray -> List(value.length()) { displayText(value.opt(it)) }.filter { it.isNotBlank() }.joinToString("\n")
        is JSONObject -> {
            val direct = value.optString("content").ifBlank { value.optString("text") }
            if (direct.isNotBlank()) direct else displayText(value.opt("content"))
        }
        else -> ""
    }.trim()
}

fun conversationRows(raw: String): List<Pair<String, String>> {
    val items = arrayOf(raw, "items")
    return List(items.length()) { index ->
        val item = items.optJSONObject(index) ?: return@List "" to ""
        val id = item.optString("conversation_id")
        id to item.optString("name").ifBlank { "未命名对话" }
    }.filter { it.first.isNotBlank() }
}

fun messageLines(raw: String): List<Pair<Boolean, String>> {
    val items = arrayOf(raw, "items")
    return List(items.length()) { index ->
        val item = items.optJSONObject(index) ?: return@List false to ""
        val mine = item.optString("type") == "user"
        val text = displayText(item.opt("content"))
        mine to text
    }.filter { it.second.isNotBlank() }
}

fun modelChoices(raw: String): List<ModelChoice> {
    val items = arrayOf(raw, null)
    val choices = mutableListOf<ModelChoice>()
    for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        val id = item.optString("provider_id").ifBlank { item.optString("providerId") }
        val name = item.optString("name").ifBlank { "模型" }
        val models = item.optJSONArray("models") ?: JSONArray()
        for (modelIndex in 0 until models.length()) {
            val model = models.optString(modelIndex)
            if (id.isNotBlank() && model.isNotBlank()) choices += ModelChoice(id, name, model)
        }
    }
    return choices
}

fun confirmationLines(raw: String): List<PendingConfirmation> {
    val items = arrayOf(raw, null)
    return List(items.length()) { index ->
        val item = items.optJSONObject(index) ?: return@List PendingConfirmation("", "", "")
        val options = item.optJSONArray("options")
        val option = encodeJson(options?.optJSONObject(0)?.opt("value"))
        PendingConfirmation(
            item.optString("call_id").ifBlank { item.optString("callId") },
            item.optString("description").ifBlank { item.optString("title").ifBlank { "需要确认" } },
            option,
        )
    }.filter { it.callId.isNotBlank() }
}

fun libraryRows(raw: String): List<String> {
    val items = arrayOf(raw, "items")
    return List(items.length()) { index ->
        val item = items.optJSONObject(index)
        if (item == null) items.optString(index) else item.optString("name").ifBlank { item.optString("title") }.ifBlank { item.optString("id") }
    }.map { it.trim() }.filter { it.isNotBlank() }
}

fun latestMessageId(raw: String): String? {
    val items = arrayOf(raw, "items")
    if (items.length() == 0) return null
    val item = items.optJSONObject(items.length() - 1) ?: return null
    return item.optString("msg_id").ifBlank { item.optString("message_id") }.ifBlank { null }
}

private fun arrayOf(raw: String, preferred: String?): JSONArray {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return JSONArray()
    val data = root.opt("data") ?: root
    return when (data) {
        is JSONArray -> data
        is JSONObject -> {
            if (preferred != null) data.optJSONArray(preferred) ?: data.optJSONArray("list") ?: JSONArray().put(data)
            else data.optJSONArray("items") ?: data.optJSONArray("list") ?: JSONArray().put(data)
        }
        else -> JSONArray()
    }
}

private fun encodeJson(value: Any?): String {
    return when (value) {
        null, JSONObject.NULL -> "\"allow\""
        is String -> JSONObject.quote(value)
        is JSONObject, is JSONArray -> value.toString()
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
}
