package com.runerback.keyboard.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.net.Socket

object KeyboardClient {

    private const val TAG = "KeyboardClient"

    sealed class State {
        object Disconnected : State()
        object Connecting : State()
        data class Connected(val host: String, val port: Int) : State()
        data class Error(val message: String) : State()
    }

    sealed class AuthState {
        object Unknown : AuthState()
        object Authenticated : AuthState()
        object PairingRequired : AuthState()
        object Failed : AuthState()
    }

    private sealed class OutgoingMessage {
        data class Key(val vk: Int, val action: String) : OutgoingMessage()
        data class Config(val interceptRealKeyboard: Boolean) : OutgoingMessage()
        data class Auth(val token: String) : OutgoingMessage()
        data class Pair(val code: String) : OutgoingMessage()
    }

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventChannel = Channel<OutgoingMessage>(Channel.UNLIMITED)

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var reader: BufferedReader? = null

    private var sendJob: Job? = null
    private var receiveJob: Job? = null
    private var lastHost: String = ""
    private var lastPort: Int = 50051
    private var lastDeviceToken: String = ""
    private var lastInterceptRealKeyboard: Boolean = false

    private const val MAX_PENDING_KEYS = 64
    private val pendingKeys = mutableListOf<OutgoingMessage.Key>()
    private var pendingConfig = false

    private fun isAuthenticated(): Boolean = _authState.value is AuthState.Authenticated

    private fun clearPending() {
        synchronized(pendingKeys) {
            pendingKeys.clear()
        }
        pendingConfig = false
    }

    private fun flushPending() {
        if (pendingConfig) {
            pendingConfig = false
            sendConfig(lastInterceptRealKeyboard)
        }
        val keys: List<OutgoingMessage.Key>
        synchronized(pendingKeys) {
            keys = pendingKeys.toList()
            pendingKeys.clear()
        }
        keys.forEach { sendKey(it.vk, it.action) }
    }

    init {
        startSendLoop()
    }

    fun connect(host: String, port: Int, deviceToken: String = "") {
        lastHost = host
        lastPort = port
        lastDeviceToken = deviceToken

        disconnect()
        attemptConnect(host, port)
    }

    fun disconnect() {
        closeSocket()
        _state.value = State.Disconnected
        _authState.value = AuthState.Unknown
        clearPending()
    }

    fun reset(host: String, port: Int, deviceToken: String = "") {
        if (host.isBlank()) return
        disconnect()
        lastHost = host
        lastPort = port
        lastDeviceToken = deviceToken
        attemptConnect(host, port)
    }

    fun sendKey(vk: Int, action: String) {
        if (!isAuthenticated()) {
            synchronized(pendingKeys) {
                if (pendingKeys.size >= MAX_PENDING_KEYS) {
                    pendingKeys.removeFirst()
                }
                pendingKeys.add(OutgoingMessage.Key(vk, action))
            }
            return
        }
        val result = eventChannel.trySend(OutgoingMessage.Key(vk, action))
        if (result.isFailure) {
            Log.w(TAG, "Dropped key event: $vk $action")
        }
    }

    fun sendConfig(interceptRealKeyboard: Boolean) {
        lastInterceptRealKeyboard = interceptRealKeyboard
        if (!isAuthenticated()) {
            pendingConfig = true
            return
        }
        val result = eventChannel.trySend(OutgoingMessage.Config(interceptRealKeyboard))
        if (result.isFailure) {
            Log.w(TAG, "Dropped config event: interceptRealKeyboard=$interceptRealKeyboard")
        }
    }

    fun sendAuth(token: String) {
        lastDeviceToken = token
        val result = eventChannel.trySend(OutgoingMessage.Auth(token))
        if (result.isFailure) {
            Log.w(TAG, "Dropped auth event")
        }
    }

    fun sendPair(code: String) {
        val result = eventChannel.trySend(OutgoingMessage.Pair(code))
        if (result.isFailure) {
            Log.w(TAG, "Dropped pair event")
        }
    }

    private fun startSendLoop() {
        if (sendJob != null) return
        sendJob = scope.launch {
            sendLoop()
        }
    }

    private fun attemptConnect(host: String, port: Int) {
        _state.value = State.Connecting
        _authState.value = AuthState.Unknown
        scope.launch {
            runCatching {
                val newSocket = Socket(host, port)
                socket = newSocket
                writer = newSocket.getOutputStream().bufferedWriter()
                reader = BufferedReader(InputStreamReader(newSocket.getInputStream()))
                _state.value = State.Connected(host, port)
                Log.i(TAG, "Connected to $host:$port")

                startReceiveLoop(newSocket)

                if (lastDeviceToken.isNotBlank()) {
                    sendAuth(lastDeviceToken)
                }
                sendConfig(lastInterceptRealKeyboard)
            }.onFailure { throwable ->
                val message = throwable.message ?: throwable.javaClass.simpleName
                _state.value = State.Error(message)
                _authState.value = AuthState.Unknown
                clearPending()
                Log.e(TAG, "Failed to connect to $host:$port", throwable)
            }
        }
    }

    private fun startReceiveLoop(socket: Socket) {
        val currentReader = reader ?: return
        receiveJob?.cancel()
        receiveJob = scope.launch {
            runCatching {
                receiveLoop(socket, currentReader)
            }.onFailure { throwable ->
                Log.e(TAG, "Receive loop failed", throwable)
            }
        }
    }

    private fun closeSocket(expectedSocket: Socket? = null) {
        receiveJob?.cancel()
        receiveJob = null

        val oldSocket = socket
        if (expectedSocket != null && oldSocket !== expectedSocket) {
            // The connection that failed is no longer the active one. Close only
            // the stale socket and leave the new connection untouched.
            scope.launch {
                runCatching { expectedSocket.close() }
            }
            return
        }

        socket = null
        writer = null
        reader = null
        scope.launch {
            runCatching {
                oldSocket?.close()
            }.onFailure {
                Log.w(TAG, "Error closing socket", it)
            }
        }
    }

    private suspend fun sendLoop() {
        while (true) {
            val event = try {
                eventChannel.receive()
            } catch (_: CancellationException) {
                break
            }

            val currentWriter = writer
            val currentSocket = socket
            if (currentWriter == null || currentSocket == null) continue

            val payload = when (event) {
                is OutgoingMessage.Key -> JSONObject().apply {
                    put("type", "key")
                    put("vk", event.vk)
                    put("action", event.action)
                }.toString()
                is OutgoingMessage.Config -> JSONObject().apply {
                    put("type", "config")
                    put("intercept_real_keyboard", event.interceptRealKeyboard)
                }.toString()
                is OutgoingMessage.Auth -> JSONObject().apply {
                    put("type", "auth")
                    put("token", event.token)
                }.toString()
                is OutgoingMessage.Pair -> JSONObject().apply {
                    put("type", "pair")
                    put("code", event.code)
                }.toString()
            }

            runCatching {
                currentWriter.write(payload)
                currentWriter.newLine()
                currentWriter.flush()
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to send event", throwable)
                if (throwable is IOException && this.socket === currentSocket) {
                    _state.value = State.Error(throwable.message ?: throwable.javaClass.simpleName)
                    _authState.value = AuthState.Unknown
                    clearPending()
                    closeSocket(currentSocket)
                }
            }
        }
    }

    private suspend fun receiveLoop(socket: Socket, reader: BufferedReader) {
        while (true) {
            val line = try {
                reader.readLine()
            } catch (_: CancellationException) {
                break
            } catch (e: IOException) {
                Log.e(TAG, "Read failed", e)
                // Only update global state if this is still the active connection.
                // An old, stale receive loop may fail after a reconnect and must
                // not close the new socket.
                if (this.socket === socket) {
                    _state.value = State.Error(e.message ?: e.javaClass.simpleName)
                    _authState.value = AuthState.Unknown
                    clearPending()
                    closeSocket(socket)
                }
                break
            } ?: break

            if (line.isBlank()) continue

            runCatching {
                val json = JSONObject(line)
                when (json.optString("type")) {
                    "auth_ok" -> {
                        _authState.value = AuthState.Authenticated
                        flushPending()
                        Log.i(TAG, "Authenticated")
                    }
                    "auth_required" -> {
                        _authState.value = AuthState.PairingRequired
                        Log.i(TAG, "Pairing required")
                    }
                    "auth_failed" -> {
                        _authState.value = AuthState.Failed
                        clearPending()
                        Log.w(TAG, "Authentication failed")
                    }
                    "token" -> {
                        val token = json.optString("token", "")
                        if (token.isNotBlank()) {
                            lastDeviceToken = token
                            com.runerback.keyboard.data.SettingsRepository.setDeviceToken(token)
                            sendAuth(token)
                            Log.i(TAG, "Received device token")
                        } else {
                            // ignore empty token
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unknown server message type: ${json.optString("type")}")
                    }
                }
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to parse server message: $line", throwable)
            }
        }
    }
}
