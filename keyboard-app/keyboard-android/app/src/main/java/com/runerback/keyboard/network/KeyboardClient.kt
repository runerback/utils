package com.runerback.keyboard.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private const val INITIAL_RECONNECT_DELAY_MS = 1000L
    private const val MAX_RECONNECT_DELAY_MS = 30000L

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
    private var reconnectJob: Job? = null
    private var lastHost: String = ""
    private var lastPort: Int = 50051
    private var lastDeviceToken: String = ""
    private var lastInterceptRealKeyboard: Boolean = false
    private var reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS

    init {
        startSendLoop()
    }

    fun connect(host: String, port: Int, deviceToken: String = "") {
        lastHost = host
        lastPort = port
        lastDeviceToken = deviceToken
        reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
        cancelReconnect()

        if (_state.value is State.Connecting || _state.value is State.Connected) {
            disconnect()
        }

        attemptConnect(host, port)
    }

    fun disconnect() {
        cancelReconnect()
        closeSocket()
        _state.value = State.Disconnected
        _authState.value = AuthState.Unknown
    }

    fun sendKey(vk: Int, action: String) {
        val result = eventChannel.trySend(OutgoingMessage.Key(vk, action))
        if (result.isFailure) {
            Log.w(TAG, "Dropped key event: $vk $action")
        }
    }

    fun sendConfig(interceptRealKeyboard: Boolean) {
        lastInterceptRealKeyboard = interceptRealKeyboard
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
                reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
                _state.value = State.Connected(host, port)
                Log.i(TAG, "Connected to $host:$port")

                startReceiveLoop()

                if (lastDeviceToken.isNotBlank()) {
                    sendAuth(lastDeviceToken)
                }
                sendConfig(lastInterceptRealKeyboard)
            }.onFailure { throwable ->
                val message = throwable.message ?: throwable.javaClass.simpleName
                _state.value = State.Error(message)
                _authState.value = AuthState.Unknown
                Log.e(TAG, "Failed to connect to $host:$port", throwable)
                scheduleReconnect()
            }
        }
    }

    private fun startReceiveLoop() {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            runCatching {
                receiveLoop()
            }.onFailure { throwable ->
                Log.e(TAG, "Receive loop failed", throwable)
            }
        }
    }

    private fun scheduleReconnect() {
        if (lastHost.isBlank()) return
        cancelReconnect()
        reconnectJob = scope.launch {
            Log.i(TAG, "Reconnecting in ${reconnectDelayMs}ms...")
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            attemptConnect(lastHost, lastPort)
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun closeSocket() {
        receiveJob?.cancel()
        receiveJob = null

        val oldSocket = socket
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
            if (currentWriter == null) continue

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
                if (throwable is IOException) {
                    closeSocket()
                    _state.value = State.Error(throwable.message ?: throwable.javaClass.simpleName)
                    _authState.value = AuthState.Unknown
                    scheduleReconnect()
                }
            }
        }
    }

    private suspend fun receiveLoop() {
        val currentReader = reader ?: return
        while (true) {
            val line = try {
                currentReader.readLine()
            } catch (_: CancellationException) {
                break
            } catch (e: IOException) {
                Log.e(TAG, "Read failed", e)
                closeSocket()
                _state.value = State.Error(e.message ?: e.javaClass.simpleName)
                _authState.value = AuthState.Unknown
                scheduleReconnect()
                break
            } ?: break

            if (line.isBlank()) continue

            runCatching {
                val json = JSONObject(line)
                when (json.optString("type")) {
                    "auth_ok" -> {
                        _authState.value = AuthState.Authenticated
                        Log.i(TAG, "Authenticated")
                    }
                    "auth_required" -> {
                        _authState.value = AuthState.PairingRequired
                        Log.i(TAG, "Pairing required")
                    }
                    "auth_failed" -> {
                        _authState.value = AuthState.Failed
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
