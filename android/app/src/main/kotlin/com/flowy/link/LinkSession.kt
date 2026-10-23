package com.flowy.link

import android.content.Context
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class PairResult(
    val status: String,
    val deviceId: String?,
    val session: HostSession,
)

data class HostSession(
    val name: String,
    val fingerprint: String,
    val port: Int,
    val addresses: List<String>,
    val accessToken: String,
    val refreshToken: String,
    val deviceId: String,
    val wakeEnabled: Boolean,
)

fun pairWithHost(context: Context, rawUrl: String): PairResult {
    val parsed = parsePairingUrl(rawUrl.trim())
    val fingerprint = deviceFingerprint(context)
    val body = JSONObject()
        .put("nonce", parsed.nonce)
        .put("displayName", Build.MODEL.ifBlank { "Android" })
        .put("platform", "android")
        .put("publicKeyFingerprint", fingerprint)
        .put("appVersion", "0.1.0")
        .toString()
    var lastError: Exception? = null
    for (address in parsed.addresses) {
        try {
            val client = pinnedClient(parsed.fingerprint)
            val root = "https://$address:${parsed.port}"
            val presented = client.newCall(
                Request.Builder()
                    .url("$root/api/link/pair")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            presented.use { response ->
                if (!response.isSuccessful) {
                    error("电脑拒绝了配对请求（${response.code}）")
                }
            }
            repeat(40) {
                Thread.sleep(1500)
                val status = client.newCall(
                    Request.Builder()
                        .url("$root/api/link/pair/status")
                        .header("x-flowy-pair-nonce", parsed.nonce)
                        .build()
                ).execute()
                val text = status.use { response ->
                    if (!response.isSuccessful) error("读取配对状态失败（${response.code}）")
                    response.body?.string().orEmpty()
                }
                val json = JSONObject(text)
                when (json.optString("status")) {
                    "approved" -> {
                        val access = json.optString("accessToken")
                        val refresh = json.optString("refreshToken")
                        val deviceId = json.optString("deviceId")
                        val session = HostSession(
                            parsed.name,
                            parsed.fingerprint,
                            parsed.port,
                            parsed.addresses,
                            access,
                            refresh,
                            deviceId,
                            true,
                        )
                        saveSession(context, session)
                        callHost(context, session, "GET", "/api/link/whoami")
                        return PairResult("已连接 ${parsed.name}", deviceId, session)
                    }
                    "denied" -> error("这台电脑拒绝了这台手机")
                    "expired", "consumed" -> error("配对码已失效，请在电脑上重新生成")
                }
            }
            error("还在等待电脑上的允许")
        } catch (error: Exception) {
            lastError = error
        }
    }
    throw lastError ?: IllegalStateException("没有可连接的地址")
}

private fun pinnedClient(expectedFingerprint: String): OkHttpClient {
    val trust = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("missing certificate")
            val digest = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
            val actual = digest.joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedFingerprint, ignoreCase = true)) {
                throw CertificateException("certificate fingerprint mismatch")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val context = SSLContext.getInstance("TLS")
    context.init(null, arrayOf<TrustManager>(trust), SecureRandom())
    return OkHttpClient.Builder()
        .sslSocketFactory(context.socketFactory, trust)
        .hostnameVerifier { _, _ -> true }
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
}

private fun deviceFingerprint(context: Context): String {
    val prefs = context.getSharedPreferences("flowy-link", Context.MODE_PRIVATE)
    prefs.getString("fingerprint", null)?.let { return it }
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    val fingerprint = bytes.joinToString("") { "%02x".format(it) }
    prefs.edit().putString("fingerprint", fingerprint).apply()
    return fingerprint
}

private const val SESSION_KEY = "session"
private const val CACHE_KEY = "conversation-cache"

fun loadSession(context: Context): HostSession? {
    val raw = context.getSharedPreferences("flowy-link", Context.MODE_PRIVATE).getString(SESSION_KEY, null) ?: return null
    val json = JSONObject(raw)
    val addresses = json.optJSONArray("addresses") ?: return null
    return HostSession(
        json.optString("name"),
        json.optString("fingerprint"),
        json.optInt("port"),
        List(addresses.length()) { addresses.getString(it) },
        json.optString("accessToken"),
        json.optString("refreshToken"),
        json.optString("deviceId"),
        json.optBoolean("wakeEnabled", true),
    )
}

fun saveSession(context: Context, session: HostSession) {
    val addresses = org.json.JSONArray()
    session.addresses.forEach { addresses.put(it) }
    val json = JSONObject()
        .put("name", session.name)
        .put("fingerprint", session.fingerprint)
        .put("port", session.port)
        .put("addresses", addresses)
        .put("accessToken", session.accessToken)
        .put("refreshToken", session.refreshToken)
        .put("deviceId", session.deviceId)
        .put("wakeEnabled", session.wakeEnabled)
    context.getSharedPreferences("flowy-link", Context.MODE_PRIVATE).edit().putString(SESSION_KEY, json.toString()).apply()
}

fun clearSession(context: Context) {
    context.getSharedPreferences("flowy-link", Context.MODE_PRIVATE).edit().remove(SESSION_KEY).apply()
}

fun cachedConversations(context: Context): List<String> {
    val raw = context.getSharedPreferences("flowy-link", Context.MODE_PRIVATE).getString(CACHE_KEY, null) ?: return emptyList()
    val array = org.json.JSONArray(raw)
    return List(array.length()) { array.getString(it) }
}

fun callHost(context: Context, session: HostSession, method: String, path: String, body: String? = null): String {
    var current = session
    var last: Exception? = null
    for (address in current.addresses) {
        try {
            val client = pinnedClient(current.fingerprint)
            var response = send(client, address, current.port, current.accessToken, method, path, body, null)
            if (response.code == 401) {
                current = refresh(context, client, address, current)
                response.close()
                response = send(client, address, current.port, current.accessToken, method, path, body, null)
            }
            val text = response.use { result ->
                val payload = result.body?.string().orEmpty()
                if (result.code == 403 && payload.contains("link step-up")) {
                    val grant = requestDesktopConfirm(client, address, current, method, path)
                    val retried = send(client, address, current.port, current.accessToken, method, path, body, grant)
                    retried.use { second ->
                        val confirmed = second.body?.string().orEmpty()
                        if (!second.isSuccessful) error("电脑返回 ${second.code} $confirmed")
                        confirmed
                    }
                } else {
                    if (!result.isSuccessful) error("电脑返回 ${result.code} $payload")
                    payload
                }
            }
            return text
        } catch (error: Exception) {
            last = error
        }
    }
    throw last ?: IllegalStateException("没有可连接的地址")
}

private fun send(
    client: OkHttpClient,
    address: String,
    port: Int,
    token: String,
    method: String,
    path: String,
    body: String?,
    stepUp: String?,
): okhttp3.Response {
    val builder = Request.Builder()
        .url("https://$address:$port$path")
        .header("Authorization", "Bearer $token")
    if (stepUp != null) builder.header("x-flowy-step-up", stepUp)
    val json = "application/json".toMediaType()
    builder.method(method, if (method == "GET") null else (body ?: "{}").toRequestBody(json))
    return client.newCall(builder.build()).execute()
}

private fun requestDesktopConfirm(
    client: OkHttpClient,
    address: String,
    session: HostSession,
    method: String,
    path: String,
): String {
    val body = JSONObject().put("method", method).put("path", path).toString()
    val opened = send(client, address, session.port, session.accessToken, "POST", "/api/link/step-up", body, null)
    val id = opened.use { result ->
        val payload = result.body?.string().orEmpty()
        if (!result.isSuccessful) error("电脑返回 ${result.code} $payload")
        JSONObject(payload).getString("id")
    }
    repeat(40) {
        Thread.sleep(1500)
        val polled = send(client, address, session.port, session.accessToken, "GET", "/api/link/step-up/status", null, id)
        val status = polled.use { result ->
            val payload = result.body?.string().orEmpty()
            if (!result.isSuccessful) error("电脑返回 ${result.code} $payload")
            JSONObject(payload).optString("status")
        }
        when (status) {
            "approved" -> return id
            "denied", "expired", "missing" -> error("电脑没有允许这次操作")
        }
    }
    error("等待电脑确认超时")
}

private fun refresh(context: Context, client: OkHttpClient, address: String, session: HostSession): HostSession {
    val body = JSONObject().put("refreshToken", session.refreshToken).toString()
    val response = client.newCall(
        Request.Builder()
            .url("https://$address:${session.port}/api/link/session/refresh")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    ).execute()
    val text = response.use { result ->
        if (!result.isSuccessful) error("登录已失效，请重新配对")
        result.body?.string().orEmpty()
    }
    val updated = session.copy(accessToken = JSONObject(text).optString("accessToken"))
    saveSession(context, updated)
    return updated
}

fun mergeDiscoveredAddress(context: Context, session: HostSession): HostSession {
    val found = discoverFingerprint(context, session.fingerprint) ?: return session
    if (session.addresses.contains(found)) return session
    val updated = session.copy(addresses = listOf(found) + session.addresses)
    saveSession(context, updated)
    return updated
}

private fun discoverFingerprint(context: Context, expected: String): String? {
    return try {
        val manager = context.getSystemService(Context.NSD_SERVICE) as android.net.nsd.NsdManager
        val latch = java.util.concurrent.CountDownLatch(1)
        val found = java.util.concurrent.atomic.AtomicReference<String>()
        val listener = object : android.net.nsd.NsdManager.DiscoveryListener {
            override fun onServiceFound(service: android.net.nsd.NsdManager.NsdServiceInfo) {
                manager.resolveService(service, object : android.net.nsd.NsdManager.ResolveListener {
                    override fun onServiceResolved(info: android.net.nsd.NsdManager.NsdServiceInfo) {
                        val fingerprint = info.attributes["fp"]?.toString(Charsets.UTF_8)
                        if (fingerprint.equals(expected, ignoreCase = true)) {
                            found.set(info.host.hostAddress)
                            latch.countDown()
                        }
                    }

                    override fun onResolveFailed(serviceInfo: android.net.nsd.NsdManager.NsdServiceInfo, errorCode: Int) = Unit
                })
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(service: android.net.nsd.NsdManager.NsdServiceInfo) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                latch.countDown()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        manager.discoverServices("_flowy-link._tcp", android.net.nsd.NsdManager.PROTOCOL_DNS_SD, listener)
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        runCatching { manager.stopServiceDiscovery(listener) }
        found.get()
    } catch (_: Exception) {
        null
    }
}
