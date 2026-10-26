package com.runerback.homeassistant.ui.devices

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.homeassistant.data.local.SettingsRepository
import com.runerback.homeassistant.data.remote.ApiClient
import com.runerback.homeassistant.data.remote.model.BleDevice
import com.runerback.homeassistant.data.remote.model.BluetoothStatus
import com.runerback.homeassistant.data.remote.model.Device
import com.runerback.homeassistant.data.remote.model.DeviceEvent
import com.runerback.homeassistant.data.remote.model.PairingResult
import com.runerback.homeassistant.util.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class DevicesViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _scanDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanDevices: StateFlow<List<BleDevice>> = _scanDevices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pairingDevice = MutableStateFlow<BleDevice?>(null)
    val pairingDevice: StateFlow<BleDevice?> = _pairingDevice.asStateFlow()

    private val _pairingStatus = MutableStateFlow(PairingStatus.IDLE)
    val pairingStatus: StateFlow<PairingStatus> = _pairingStatus.asStateFlow()

    private val _pendingDeviceId = MutableStateFlow<String?>(null)
    val pendingDeviceId: StateFlow<String?> = _pendingDeviceId.asStateFlow()

    private val _bluetoothStatus = MutableStateFlow<BluetoothStatus?>(null)
    val bluetoothStatus: StateFlow<BluetoothStatus?> = _bluetoothStatus.asStateFlow()

    private val _bluetoothBusy = MutableStateFlow(false)
    val bluetoothBusy: StateFlow<Boolean> = _bluetoothBusy.asStateFlow()

    private var scanJob: Job? = null
    private var eventsJob: Job? = null

    init {
        load()
        refreshBluetoothStatus()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val baseUrl = settingsRepository.serverUrl.first()
            ApiClient.get<List<Device>>(baseUrl, "/api/devices")
                .onSuccess {
                    LogBuffer.append("Loaded ${it.size} devices")
                    _devices.value = it
                }
                .onFailure {
                    LogBuffer.append("Failed to load devices: ${it.message}")
                    _error.value = it.message
                }
            _loading.value = false
        }
    }

    fun refreshBluetoothStatus() {
        viewModelScope.launch {
            val baseUrl = settingsRepository.serverUrl.first()
            ApiClient.get<BluetoothStatus>(baseUrl, "/api/system/bluetooth")
                .onSuccess { _bluetoothStatus.value = it }
                .onFailure { LogBuffer.append("Failed to load Bluetooth status: ${it.message}") }
        }
    }

    fun toggleBluetooth() {
        val current = _bluetoothStatus.value ?: return
        viewModelScope.launch {
            _bluetoothBusy.value = true
            _error.value = null
            val baseUrl = settingsRepository.serverUrl.first()
            ApiClient.postFormAndParse<BluetoothStatus>(
                baseUrl,
                "/api/system/bluetooth/power",
                mapOf("on" to (!current.powered).toString()),
            )
                .onSuccess {
                    LogBuffer.append("Server Bluetooth powered ${if (it.powered) "on" else "off"}")
                    _bluetoothStatus.value = it
                }
                .onFailure {
                    LogBuffer.append("Failed to toggle Bluetooth: ${it.message}")
                    _error.value = it.message
                }
            _bluetoothBusy.value = false
        }
    }

    fun startScan() {
        viewModelScope.launch {
            _scanDevices.value = emptyList()
            _scanning.value = true
            _error.value = null
            val baseUrl = settingsRepository.serverUrl.first()
            ApiClient.postForm(baseUrl, "/api/devices/ble-scan/start", emptyMap())
                .onSuccess {
                    LogBuffer.append("BLE scan started")
                    observeBleScan(baseUrl)
                }
                .onFailure {
                    LogBuffer.append("Failed to start BLE scan: ${it.message}")
                    _error.value = it.message
                    _scanning.value = false
                }
        }
    }

    private fun observeBleScan(baseUrl: String) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val client = ApiClient.client.newBuilder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                })
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
            val url = "$baseUrl/api/devices/ble-scan"
            val request = Request.Builder().url(url).build()
            LogBuffer.append("BLE SSE connecting to $url")

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        LogBuffer.append("BLE SSE failed: HTTP ${response.code}")
                        _scanning.value = false
                        return@launch
                    }
                    val source = response.body?.source() ?: run {
                        LogBuffer.append("BLE SSE empty body")
                        _scanning.value = false
                        return@launch
                    }
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty()) continue
                        try {
                            val device = ApiClient.json.decodeFromString(
                                BleDevice.serializer(),
                                payload,
                            )
                            _scanDevices.value = (_scanDevices.value.filter { it.address != device.address } + device)
                                .sortedByDescending { it.rssi }
                            LogBuffer.append("BLE device ${device.name} (${device.rssi})")
                        } catch (_: Exception) {
                            // Ignore malformed lines.
                        }
                    }
                }
            } catch (e: Exception) {
                LogBuffer.append("BLE SSE error: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                _scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
        _scanDevices.value = emptyList()
    }

    fun selectDevice(device: BleDevice) {
        _pairingDevice.value = device
        _pairingStatus.value = PairingStatus.IDLE
        _pendingDeviceId.value = null
        _error.value = null
    }

    fun clearSelectedDevice() {
        _pairingDevice.value = null
        _pairingStatus.value = PairingStatus.IDLE
        _pendingDeviceId.value = null
    }

    fun pairDevice(name: String, ssid: String, password: String) {
        val device = _pairingDevice.value ?: return
        viewModelScope.launch {
            _pairingStatus.value = PairingStatus.PAIRING
            _pendingDeviceId.value = null
            _error.value = null
            val baseUrl = settingsRepository.serverUrl.first()
            ApiClient.postFormAndParse<PairingResult>(
                baseUrl,
                "/api/devices/pair",
                mapOf(
                    "ble_address" to device.address,
                    "name" to name.ifBlank { device.name },
                    "ssid" to ssid,
                    "password" to password,
                ),
            )
                .onSuccess { result ->
                    LogBuffer.append("Pairing started for ${result.deviceId}")
                    _pendingDeviceId.value = result.deviceId
                    observeClaimEvents(baseUrl, result.deviceId)
                }
                .onFailure {
                    LogBuffer.append("Failed to pair: ${it.message}")
                    _pairingStatus.value = PairingStatus.ERROR
                    _error.value = it.message
                }
        }
    }

    private fun observeClaimEvents(baseUrl: String, deviceId: String) {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch(Dispatchers.IO) {
            val client = ApiClient.client.newBuilder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                })
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
            val url = "$baseUrl/api/messages/stream"
            val request = Request.Builder().url(url).build()
            LogBuffer.append("Device events SSE connecting to $url")

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        LogBuffer.append("Device events SSE failed: HTTP ${response.code}")
                        return@launch
                    }
                    val source = response.body?.source() ?: return@launch
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty()) continue
                        try {
                            val event = ApiClient.json.decodeFromString(
                                DeviceEvent.serializer(),
                                payload,
                            )
                            if (event.type == "device_claimed" && event.deviceId == deviceId) {
                                LogBuffer.append("Device $deviceId claimed")
                                _pairingStatus.value = PairingStatus.SUCCESS
                                load()
                                return@launch
                            }
                        } catch (_: Exception) {
                            // Ignore malformed lines.
                        }
                    }
                }
            } catch (e: Exception) {
                LogBuffer.append("Device events SSE error: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    fun dismissPairing() {
        eventsJob?.cancel()
        eventsJob = null
        _pairingDevice.value = null
        _pairingStatus.value = PairingStatus.IDLE
        _pendingDeviceId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        eventsJob?.cancel()
    }

    enum class PairingStatus {
        IDLE,
        PAIRING,
        SUCCESS,
        ERROR,
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                DevicesViewModel(SettingsRepository(application.applicationContext))
            }
        }
    }
}
