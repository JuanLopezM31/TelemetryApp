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

class GatewayViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "GatewayViewModel"

    private val bleManager  = BleManager(application.applicationContext)
    private val mqttManager = MqttManager()

    // ── Estado UI ─────────────────────────────────────────────────────────────
    val bleState  : StateFlow<BleConnectionState>  = bleManager.connectionState
    val mqttState : StateFlow<MqttConnectionState> = mqttManager.connectionState

    private val _currentRpm = MutableStateFlow(0)
    val currentRpm: StateFlow<Int> = _currentRpm.asStateFlow()

    private val _speed = MutableStateFlow(128)          // Velocidad inicial al 50%
    val speed: StateFlow<Int> = _speed.asStateFlow()

    private val _direction = MutableStateFlow("STOP")
    val direction: StateFlow<String> = _direction.asStateFlow()

    /**
     * Job del loop de comando continuo.
     * Se cancela automáticamente al soltar el botón (stop()).
     */
    private var commandLoopJob: Job? = null

    /**
     * Intervalo de reenvío del comando (ms).
     * Debe ser MENOR que el WATCHDOG_TIMEOUT_MS del ESP32 (1500ms).
     * Usamos 500ms → margen amplio de seguridad (3x antes del watchdog).
     */
    private val COMMAND_REPEAT_MS = 500L

    init {
        connectMqtt()
        observeRpmTelemetry()
    }

    private fun connectMqtt() {
        viewModelScope.launch(Dispatchers.IO) {
            mqttManager.connect()
        }
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

    // ── BLE ──────────────────────────────────────────────────────────────────

    fun startBleScan() {
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.startScan()
        }
    }

    fun disconnectBle() {
        stopCommandLoop()
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.disconnect()
        }
    }

    // ── COMANDOS DE MOVIMIENTO ───────────────────────────────────────────────

    fun moveForward()  = startCommandLoop(BleConstants.DIR_FORWARD,  "ADELANTE")
    fun moveBackward() = startCommandLoop(BleConstants.DIR_BACKWARD, "ATRÁS")
    fun moveLeft()     = startCommandLoop(BleConstants.DIR_LEFT,     "IZQUIERDA")
    fun moveRight()    = startCommandLoop(BleConstants.DIR_RIGHT,    "DERECHA")

    fun stop() {
        stopCommandLoop()
        // Enviar STOP una sola vez (no necesita repetición)
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.sendControlCommand(BleConstants.DIR_STOP, 0x00)
            Log.d(TAG, "STOP enviado")
        }
        _direction.value = "STOP"
    }

    fun updateSpeed(value: Int) {
        _speed.value = value.coerceIn(0, 255)
    }

    /**
     * Inicia un loop que reenvía el comando cada [COMMAND_REPEAT_MS] ms
     * mientras el botón esté presionado.
     *
     * Esto alimenta el watchdog del ESP32 (WATCHDOG_TIMEOUT_MS = 1500ms)
     * evitando que detenga los motores por "pérdida de señal".
     */
    private fun startCommandLoop(dirByte: Byte, label: String) {
        // Cancelar cualquier loop anterior antes de iniciar uno nuevo
        stopCommandLoop()
        _direction.value = label

        commandLoopJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Loop iniciado: $label @ ${_speed.value} PWM")
            while (true) {
                val speedByte = _speed.value.toByte()
                bleManager.sendControlCommand(dirByte, speedByte)
                delay(COMMAND_REPEAT_MS)
            }
            // El while(true) se rompe solo cuando el Job es cancelado (stop())
        }
    }

    private fun stopCommandLoop() {
        commandLoopJob?.cancel()
        commandLoopJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopCommandLoop()
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.disconnect()
            mqttManager.disconnect()
        }
    }
}