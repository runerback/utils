package com.runerback.brownnoise.streaming

import org.json.JSONObject
import java.net.Socket

object ControlClient {

    fun sendCommand(host: String, port: Int, command: Map<String, Any?>): Result<String> {
        return runCatching {
            Socket(host, port).use { sock ->
                val writer = sock.getOutputStream().bufferedWriter()
                val reader = sock.getInputStream().bufferedReader()
                val json = JSONObject(command).toString()
                writer.write(json)
                writer.newLine()
                writer.flush()
                reader.readLine() ?: ""
            }
        }
    }
}
