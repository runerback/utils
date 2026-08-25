package com.runerback.files.share

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLEncoder

fun getLanIpAddress(): String? {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }

        val preferred = interfaces
            .filter { it.name.startsWith("wlan", ignoreCase = true) || it.name.startsWith("ap", ignoreCase = true) }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }

        val address = preferred ?: interfaces
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
        address?.hostAddress
    } catch (e: Exception) {
        null
    }
}

fun findFreePort(): Int {
    ServerSocket(0).use { socket ->
        return socket.localPort
    }
}

private val TOKEN_CHARS = ('a'..'z') + ('A'..'Z') + ('0'..'9')

fun randomToken(length: Int = 16): String {
    return (1..length)
        .map { TOKEN_CHARS.random() }
        .joinToString("")
}

fun urlEncode(text: String): String {
    return URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")
}
