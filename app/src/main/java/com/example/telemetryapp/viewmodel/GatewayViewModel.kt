package com.example.telemetryapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.telemetryapp.ble.BleConnectionState
import com.example.telemetryapp.ble.BleConstants
import com.example.telemetryapp.ble.BleManager
import com.example.telemetryapp.mqtt.MqttConnectionState
import com.example.telemetryapp.mqtt.MqttManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

// Estados posibles del servidor MQTT
enum class ServerHealthState {
    UNKNOWN,    // Aún no se ha consultado
    UP,         // Respondió "OK"
    DOWN        // No respondió o respondió "DOWN"
}

class GatewayViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "GatewayViewModel"

    private val bleManager  = BleManager(application.applicationContext)
    private val mqttManager = MqttManager(application.applicationContext)

    // ── Estado UI ─────────────────────────────────────────────────────────────
    val bleState  : StateFlow<BleConnectionState>  = bleManager.connectionState
    val mqttState : StateFlow<MqttConnectionState> = mqttManager.connectionState

    private val _currentRpm = MutableStateFlow(0)
    val currentRpm: StateFlow<Int> = _currentRpm.asStateFlow()

    private val _speed = MutableStateFlow(128)
    val speed: StateFlow<Int> = _speed.asStateFlow()

    private val _direction = MutableStateFlow("STOP")
    val direction: StateFlow<String> = _direction.asStateFlow()

    // ── Server Health ─────────────────────────────────────────────────────────
    private val _serverHealth = MutableStateFlow(ServerHealthState.UNKNOWN)
    val serverHealth: StateFlow<ServerHealthState> = _serverHealth.asStateFlow()

    companion object {
        const val HEALTHCHECK_URL     = "http://3.20.62.117:8080"
        const val HEALTHCHECK_POLL_MS = 15_000L   // Consultar cada 15 segundos
        const val HEALTHCHECK_TIMEOUT = 5_000     // Timeout de conexión en ms
    }

    private var commandLoopJob    : Job? = null
    private var healthCheckJob    : Job? = null
    private val COMMAND_REPEAT_MS = 500L

    init {
        connectMqtt()
        observeRpmTelemetry()
        startHealthCheckPolling()   // ← inicia el polling al crear el ViewModel
    }

    // ────────────────────────────────────────────────────────────────────────
    // SERVER HEALTHCHECK
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Consulta GET http://3.20.62.117:8080 cada [HEALTHCHECK_POLL_MS] ms.
     * Si la respuesta HTTP 200 contiene "OK" → ServerHealthState.UP
     * Cualquier otro caso (timeout, error, "DOWN") → ServerHealthState.DOWN
     */
    private fun startHealthCheckPolling() {
        healthCheckJob?.cancel()
        healthCheckJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                _serverHealth.value = checkServerHealth()
                delay(HEALTHCHECK_POLL_MS)
            }
        }
    }

    private fun checkServerHealth(): ServerHealthState {
        return try {
            val url = URL(HEALTHCHECK_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod        = "GET"
                connectTimeout       = HEALTHCHECK_TIMEOUT
                readTimeout          = HEALTHCHECK_TIMEOUT
                instanceFollowRedirects = false
            }

            val responseCode = conn.responseCode
            val body = conn.inputStream.bufferedReader().readText().trim()
            conn.disconnect()

            Log.d(TAG, "Healthcheck → HTTP $responseCode | body: $body")

            if (responseCode == 200 && body.equals("OK", ignoreCase = true)) {
                ServerHealthState.UP
            } else {
                ServerHealthState.DOWN
            }
        } catch (e: Exception) {
            Log.w(TAG, "Healthcheck falló: ${e.message}")
            ServerHealthState.DOWN
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // MQTT + BLE (sin cambios)
    // ────────────────────────────────────────────────────────────────────────

    private fun connectMqtt() {
        viewModelScope.launch(Dispatchers.IO) { mqttManager.connect() }
    }

    private fun observeRpmTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.rpmFlow
                .distinctUntilChanged()
                .collect { rpm ->
                    _currentRpm.value = rpm
                    mqttManager.publishRpm(rpm)
                }
        }
    }

    fun startBleScan() {
        viewModelScope.launch(Dispatchers.IO) { bleManager.startScan() }
    }

    fun disconnectBle() {
        stopCommandLoop()
        viewModelScope.launch(Dispatchers.IO) { bleManager.disconnect() }
    }

    fun moveForward()  = startCommandLoop(BleConstants.DIR_FORWARD,  "ADELANTE")
    fun moveBackward() = startCommandLoop(BleConstants.DIR_BACKWARD, "ATRÁS")
    fun moveLeft()     = startCommandLoop(BleConstants.DIR_LEFT,     "IZQUIERDA")
    fun moveRight()    = startCommandLoop(BleConstants.DIR_RIGHT,    "DERECHA")

    fun stop() {
        stopCommandLoop()
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.sendControlCommand(BleConstants.DIR_STOP, 0x00)
        }
        _direction.value = "STOP"
    }

    fun updateSpeed(value: Int) { _speed.value = value.coerceIn(0, 255) }

    private fun startCommandLoop(dirByte: Byte, label: String) {
        stopCommandLoop()
        _direction.value = label
        commandLoopJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                bleManager.sendControlCommand(dirByte, _speed.value.toByte())
                delay(COMMAND_REPEAT_MS)
            }
        }
    }

    private fun stopCommandLoop() {
        commandLoopJob?.cancel()
        commandLoopJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopCommandLoop()
        healthCheckJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.disconnect()
            mqttManager.disconnect()
        }
    }
}