package com.example.telemetryapp.mqtt

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * MqttManager — pasarela entre los datos de telemetría BLE y el broker MQTT.
 *
 * Usa HiveMQ MQTT Client v1.x (API async/reaktive), que no bloquea el hilo
 * llamante y es compatible con Kotlin Coroutines sin adaptador extra.
 *
 * Flujo de datos:
 *   ESP32 ──BLE Notify──▶ BleManager ──rpmFlow──▶ ViewModel ──▶ MqttManager.publish()
 *
 * Formato JSON publicado:
 *   {"rpm": 1234}
 */
class MqttManager {

    private val TAG = "MqttManager"

    // ── Estado observable ────────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    // ── Cliente HiveMQ ───────────────────────────────────────────────────────
    private var mqttClient: Mqtt3AsyncClient? = null

    companion object {
        // Reemplaza con la Elastic IP que creaste en AWS
        const val BROKER_HOST = "3.20.62.117"
        const val BROKER_PORT = 1883

        // Credenciales que creaste con mosquitto_passwd
        const val MQTT_USER = "Carraso_Adminsito"
        const val MQTT_PASS = "AdCarroMin123*"

        const val TELEMETRY_TOPIC = "vehiculos/carro01/telemetria"
        const val QOS = 0
    }

    // ────────────────────────────────────────────────────────────────────────
    // CONEXIÓN
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Establece conexión con el broker MQTT.
     * Debe llamarse desde un contexto de Dispatchers.IO.
     *
     * El clientId único previene conflictos si múltiples instancias se conectan
     * al mismo broker de prueba simultáneamente.
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        if (_connectionState.value == MqttConnectionState.CONNECTED) return@withContext

        _connectionState.value = MqttConnectionState.CONNECTING
        Log.i(TAG, "Conectando a MQTT broker: $BROKER_HOST:$BROKER_PORT")

        val clientId = "iot-edge-gateway-${UUID.randomUUID().toString().take(8)}"

        val client = MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .serverHost(BROKER_HOST)
            .serverPort(BROKER_PORT)
            .automaticReconnectWithDefaultConfig() // MEJORA: Vital para un carro en movimiento si pierde señal
            .simpleAuth()                          // MEJORA: Autenticación con Mosquitto
                .username(MQTT_USER)
                .password(MQTT_PASS.toByteArray())
                .applySimpleAuth()
            .buildAsync()

        mqttClient = client

        // Conexión asíncrona → convertimos el CompletableFuture a suspend
        runCatching {
            client.connect()
                .thenAccept {
                    Log.i(TAG, "✅ MQTT conectado. ClientId: $clientId")
                    _connectionState.value = MqttConnectionState.CONNECTED
                }
                .exceptionally { throwable ->
                    Log.e(TAG, "Error al conectar MQTT: ${throwable.message}")
                    _connectionState.value = MqttConnectionState.DISCONNECTED
                    null
                }
                .get()   // Bloqueo en Dispatchers.IO (permitido)
        }.onFailure { e ->
            Log.e(TAG, "Excepción al conectar MQTT: ${e.message}")
            _connectionState.value = MqttConnectionState.DISCONNECTED
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // PUBLICACIÓN DE TELEMETRÍA
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Publica el valor de RPM en el tópico [TELEMETRY_TOPIC] en formato JSON.
     *
     * Formato: {"rpm": 1234}
     *
     * Esta función es NOT suspending porque HiveMQ ya es async internamente.
     * El ViewModel la llama desde Dispatchers.IO sin bloquearse.
     *
     * @param rpm Valor entero de revoluciones por minuto reconstruido desde BLE.
     */
    fun publishRpm(rpm: Int) {
        val client = mqttClient ?: run {
            Log.w(TAG, "publishRpm: cliente MQTT no inicializado.")
            return
        }
        if (_connectionState.value != MqttConnectionState.CONNECTED) {
            Log.w(TAG, "publishRpm: no conectado al broker. RPM=$rpm descartado.")
            return
        }

        // Serialización JSON manual (sin dependencia extra)
        // Si se usa kotlinx.serialization, cambiar por: Json.encodeToString(TelemetryPayload(rpm))
        val jsonPayload = """{"rpm": $rpm}"""
        val payloadBytes = jsonPayload.toByteArray(Charsets.UTF_8)

        client.publishWith()
            .topic(TELEMETRY_TOPIC)
            .payload(payloadBytes)
            .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_MOST_ONCE)
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "Error al publicar RPM=$rpm: ${throwable.message}")
                } else {
                    Log.v(TAG, "📡 Publicado: $jsonPayload → $TELEMETRY_TOPIC")
                }
            }
    }

    // ────────────────────────────────────────────────────────────────────────
    // DESCONEXIÓN
    // ────────────────────────────────────────────────────────────────────────

    /** Desconecta limpiamente del broker MQTT y libera el cliente. */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching {
            mqttClient?.disconnect()?.get()
        }
        mqttClient = null
        _connectionState.value = MqttConnectionState.DISCONNECTED
        Log.i(TAG, "MQTT desconectado.")
    }
}

/** Estados del ciclo de vida de la conexión MQTT. */
enum class MqttConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}