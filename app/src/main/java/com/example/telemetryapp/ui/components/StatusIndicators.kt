package com.example.telemetryapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.telemetryapp.viewmodel.ServerHealthState

private val ColorUp           = Color(0xFF4CAF50)   // Verde  — servidor UP
private val ColorDown         = Color(0xFFF44336)   // Rojo   — servidor DOWN
private val ColorConnected    = Color(0xFF4CAF50)
private val ColorConnecting   = Color(0xFFFFC107)
private val ColorDisconnected = Color(0xFFF44336)
private val ColorUnknown      = Color(0xFF9E9E9E)   // Gris   — aún sin datos

/**
 * Fila con tres badges: BLE | MQTT | Servidor
 * Se usa en GatewayScreen justo debajo del TopBar.
 */
@Composable
fun ConnectionStatusRow(
    bleState     : BleConnectionState,
    mqttState    : MqttConnectionState,
    serverHealth : ServerHealthState,
    modifier     : Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusBadge(
            label      = "BLE",
            statusText = bleState.toDisplayString(),
            color      = bleState.toColor(),
            modifier   = Modifier.weight(1f)
        )
        StatusBadge(
            label      = "MQTT",
            statusText = mqttState.toDisplayString(),
            color      = mqttState.toColor(),
            modifier   = Modifier.weight(1f)
        )
        StatusBadge(
            label      = "Servidor",
            statusText = serverHealth.toDisplayString(),
            color      = serverHealth.toColor(),
            modifier   = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusBadge(
    label      : String,
    statusText : String,
    color      : Color,
    modifier   : Modifier = Modifier
) {
    Surface(
        modifier       = modifier,
        shape          = RoundedCornerShape(12.dp),
        color          = color.copy(alpha = 0.12f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Punto indicador + nombre del protocolo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(color, CircleShape)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text       = label,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color      = color.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.height(3.dp))
            // Estado en la segunda línea — nunca se corta
            Text(
                text       = statusText,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color      = color,
                maxLines   = 1
            )
        }
    }
}

// ── Extensiones BLE ───────────────────────────────────────────────────────────

private fun BleConnectionState.toDisplayString() = when (this) {
    BleConnectionState.DISCONNECTED -> "Desconectado"
    BleConnectionState.SCANNING     -> "Escaneando..."
    BleConnectionState.CONNECTING   -> "Conectando..."
    BleConnectionState.DISCOVERING  -> "Configurando..."
    BleConnectionState.CONNECTED    -> "Conectado"
}

private fun BleConnectionState.toColor() = when (this) {
    BleConnectionState.CONNECTED                                              -> ColorConnected
    BleConnectionState.SCANNING, BleConnectionState.CONNECTING,
    BleConnectionState.DISCOVERING                                            -> ColorConnecting
    BleConnectionState.DISCONNECTED                                           -> ColorDisconnected
}

// ── Extensiones MQTT ──────────────────────────────────────────────────────────

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

// ── Extensiones ServerHealth ──────────────────────────────────────────────────

private fun ServerHealthState.toDisplayString() = when (this) {
    ServerHealthState.UNKNOWN -> "Verificando..."
    ServerHealthState.UP      -> "OK"
    ServerHealthState.DOWN    -> "DOWN"
}

private fun ServerHealthState.toColor() = when (this) {
    ServerHealthState.UP      -> ColorUp
    ServerHealthState.DOWN    -> ColorDown
    ServerHealthState.UNKNOWN -> ColorUnknown
}