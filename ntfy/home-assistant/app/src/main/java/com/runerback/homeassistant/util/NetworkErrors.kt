package com.runerback.homeassistant.util

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object NetworkErrors {

    /**
     * Maps a low-level network failure to a readable message that includes the
     * target URL and a hint for VPN users, since VPN routing is the most common
     * cause of "Failed to connect" errors.
     */
    fun describe(error: Throwable, url: String): String {
        val root = generateSequence(error as Throwable?) { it.cause }
            .firstOrNull {
                it is UnknownHostException ||
                    it is SocketTimeoutException ||
                    it is NoRouteToHostException ||
                    it is ConnectException ||
                    it is SSLException
            } ?: error
        return when (root) {
            is UnknownHostException ->
                "Cannot resolve the host in $url — check the server URL, and your VPN/DNS settings"
            is SocketTimeoutException ->
                "Connection to $url timed out — the server is not responding; if you use a VPN, make sure it is connected and routes to the server"
            is NoRouteToHostException ->
                "No route to $url — if you use a VPN, check that the tunnel routes the server's network"
            is ConnectException ->
                "Cannot connect to $url — the server is unreachable; if you use a VPN, check that it is connected and routes to the server"
            is SSLException ->
                "Secure connection to $url failed (${root.message}) — check whether the server URL should use http:// or https://"
            else -> error.message ?: "Connection failed"
        }
    }
}
