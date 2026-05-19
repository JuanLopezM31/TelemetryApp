package com.example.telemetryapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telemetryapp.ble.BleConnectionState
import com.example.telemetryapp.mqtt.MqttConnectionState

// ── Colores semáforo ──────────────────────────────────────────────────────────
private val ColorConnected    = Color(0xFF4CAF50)   // Verde
private val ColorConnecting   = Color(0xFFFFC107)   // Ámbar
private val ColorDisconnected = Color(0xFFF44336)   // Rojo

/**
 * Fila con dos indicadores de estado (BLE y MQTT).
 *
 * Cada indicador muestra:
 *   • Un punto de color (semáforo) según el estado de conexión.
 *   • Una etiqueta de texto con el protocolo y su estado.
 */
@Composable
fun ConnectionStatusRow(
    bleState: BleConnectionState,
    mqttState: MqttConnectionState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusBadge(
            label = "BLE",
            statusText = bleState.toDisplayString(),
            color = bleState.toColor()
        )
        StatusBadge(
            label = "MQTT",
            statusText = mqttState.toDisplayString(),
            color = mqttState.toColor()
        )
    }
}

/**
 * Badge individual de estado de conexión.
 *
 * Visualmente: [● PROTOCOLO: Estado]
 */
@Composable
private fun StatusBadge(
    label: String,
    statusText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Punto indicador
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label: $statusText",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

// ── Extensiones de mapeo Estado → Display ────────────────────────────────────

private fun BleConnectionState.toDisplayString() = when (this) {
    BleConnectionState.DISCONNECTED -> "Desconectado"
    BleConnectionState.SCANNING     -> "Escaneando..."
    BleConnectionState.CONNECTING   -> "Conectando..."
    BleConnectionState.DISCOVERING  -> "Configurando..."
    BleConnectionState.CONNECTED    -> "Conectado"
}

private fun BleConnectionState.toColor() = when (this) {
    BleConnectionState.CONNECTED    -> ColorConnected
    BleConnectionState.SCANNING,
    BleConnectionState.CONNECTING,
    BleConnectionState.DISCOVERING  -> ColorConnecting
    BleConnectionState.DISCONNECTED -> ColorDisconnected
}

private fun MqttConnectionState.toDisplayString() = when (this) {
    MqttConnectionState.DISCONNECTED -> "Desconectado"
    MqttConnectionState.CONNECTING   -> "Conectando..."
    MqttConnectionState.CONNECTED    -> "Conectado"
}

private fun MqttConnectionState.toColor() = when (this) {
    MqttConnectionState.CONNECTED    -> ColorConnected
    MqttConnectionState.CONNECTING   -> ColorConnecting
    MqttConnectionState.DISCONNECTED -> ColorDisconnected
}