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

data class PairResult(val status: String, val deviceId: String?)

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
                        val who = client.newCall(
                            Request.Builder()
                                .url("$root/api/link/whoami")
                                .header("Authorization", "Bearer $access")
                                .build()
                        ).execute()
                        who.use { response ->
                            if (!response.isSuccessful) error("登录没有生效（${response.code}）")
                        }
                        return PairResult("已连接 ${parsed.name}", json.optString("deviceId"))
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
