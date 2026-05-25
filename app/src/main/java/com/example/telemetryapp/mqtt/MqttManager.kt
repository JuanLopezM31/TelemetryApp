package com.example.telemetryapp.mqtt

import android.content.Context
import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory


class MqttManager(private val context: Context) {

    private val TAG = "MqttManager"

    private val _connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    private var mqttClient: Mqtt3AsyncClient? = null

    companion object {
        const val BROKER_HOST = "3.20.62.117"
        const val BROKER_PORT = 8883          // TLS

        const val MQTT_USER = "Carraso_Adminsito"
        const val MQTT_PASS = "AdCarroMin123*"

        const val TELEMETRY_TOPIC = "vehiculos/carro01/telemetria"
    }

    // Carga ca.crt desde assets y construye un SSLContext que solo confía en esa CA
    private fun buildSslContext(): SSLContext {
        val cf = CertificateFactory.getInstance("X.509")
        val caInput: InputStream = context.assets.open("ca.crt")
        val ca = caInput.use { cf.generateCertificate(it) }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("ca", ca)
        }

        val tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        ).apply { init(keyStore) }

        return SSLContext.getInstance("TLS").apply {
            init(null, tmf.trustManagers, null)
        }
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        if (_connectionState.value == MqttConnectionState.CONNECTED) return@withContext

        _connectionState.value = MqttConnectionState.CONNECTING
        Log.i(TAG, "Conectando a MQTT TLS: $BROKER_HOST:$BROKER_PORT")

        val clientId = "iot-edge-gateway-${UUID.randomUUID().toString().take(8)}"

        // Cargar CA desde assets
        val cf = CertificateFactory.getInstance("X.509")
        val ca = context.assets.open("ca.crt").use { cf.generateCertificate(it) }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("ca", ca)
        }
        val tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        ).apply { init(keyStore) }

        val client = MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .serverHost(BROKER_HOST)
            .serverPort(BROKER_PORT)
            .sslConfig()
            .trustManagerFactory(tmf)                        // ← usar tmf directamente
            .hostnameVerifier { _, _ -> true }               // ← aquí sí funciona
            .applySslConfig()
            .automaticReconnectWithDefaultConfig()
            .simpleAuth()
            .username(MQTT_USER)
            .password(MQTT_PASS.toByteArray())
            .applySimpleAuth()
            .buildAsync()

        mqttClient = client

        runCatching {
            client.connect()
                .thenAccept {
                    Log.i(TAG, "✅ MQTT TLS conectado. ClientId: $clientId")
                    _connectionState.value = MqttConnectionState.CONNECTED
                }
                .exceptionally { throwable: Throwable ->
                    Log.e(TAG, "Error al conectar MQTT TLS: ${throwable.message}")
                    _connectionState.value = MqttConnectionState.DISCONNECTED
                    null
                }
                .get()
        }.onFailure { e ->
            Log.e(TAG, "Excepción al conectar MQTT TLS: ${e.message}")
            _connectionState.value = MqttConnectionState.DISCONNECTED
        }
    }

    fun publishRpm(rpm: Int) {
        val client = mqttClient ?: run {
            Log.w(TAG, "publishRpm: cliente no inicializado.")
            return
        }
        if (_connectionState.value != MqttConnectionState.CONNECTED) {
            Log.w(TAG, "publishRpm: no conectado. RPM=$rpm descartado.")
            return
        }

        val jsonPayload = """{"rpm": $rpm}"""

        client.publishWith()
            .topic(TELEMETRY_TOPIC)
            .payload(jsonPayload.toByteArray(Charsets.UTF_8))
            .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_MOST_ONCE)
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "Error publicando RPM=$rpm: ${throwable.message}")
                } else {
                    Log.v(TAG, "📡 Publicado (TLS): $jsonPayload")
                }
            }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { mqttClient?.disconnect()?.get() }
        mqttClient = null
        _connectionState.value = MqttConnectionState.DISCONNECTED
        Log.i(TAG, "MQTT desconectado.")
    }
}

enum class MqttConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}