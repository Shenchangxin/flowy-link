package com.flowy.link

data class PairingUrl(
    val version: Int,
    val port: Int,
    val fingerprint: String,
    val nonce: String,
    val addresses: List<String>,
    val name: String,
)

fun parsePairingUrl(raw: String): PairingUrl {
    val rest = raw.removePrefix("flowy://pair?").takeIf { raw.startsWith("flowy://pair?") }
        ?: error("配对链接格式不正确")
    val fields = linkedMapOf<String, String>()
    for (pair in rest.split('&')) {
        val key = pair.substringBefore('=', "")
        val value = percentDecode(pair.substringAfter('=', ""))
        if (key.isEmpty() || !pair.contains('=')) error("配对链接格式不正确")
        fields[key] = value
    }
    val version = fields["v"]?.toIntOrNull() ?: error("配对链接格式不正确")
    val port = fields["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: error("配对链接格式不正确")
    val fingerprint = fields["fp"]?.lowercase()?.takeIf { it.length == 64 && it.all { ch -> ch.isHex() } }
        ?: error("配对链接格式不正确")
    val nonce = fields["n"]?.lowercase()?.takeIf { it.length in 16..128 && it.all { ch -> ch.isHex() } }
        ?: error("配对链接格式不正确")
    val addresses = fields["h"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    if (addresses.isEmpty()) error("配对链接格式不正确")
    val name = fields["name"] ?: error("配对链接格式不正确")
    return PairingUrl(version, port, fingerprint, nonce, addresses, name)
}

private fun Char.isHex(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun percentDecode(value: String): String {
    val bytes = ArrayList<Byte>()
    var index = 0
    val raw = value.toByteArray(Charsets.US_ASCII)
    while (index < raw.size) {
        if (raw[index] == '%'.code.toByte() && index + 2 < raw.size) {
            val hex = value.substring(index + 1, index + 3)
            val decoded = hex.toIntOrNull(16)
            if (decoded != null) {
                bytes.add(decoded.toByte())
                index += 3
                continue
            }
        }
        bytes.add(raw[index])
        index += 1
    }
    return bytes.toByteArray().toString(Charsets.UTF_8)
}
