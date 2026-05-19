package com.example.telemetryapp.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.telemetryapp.ble.BleConnectionState
import com.example.telemetryapp.ui.components.ConnectionStatusRow
import com.example.telemetryapp.ui.components.DirectionControls
import com.example.telemetryapp.viewmodel.GatewayViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * GatewayScreen — Pantalla principal de la aplicación IoT Edge Gateway.
 *
 * Contenido:
 *   ┌─────────────────────────────────────────┐
 *   │  TopBar: "IoT Edge Gateway"             │
 *   ├─────────────────────────────────────────┤
 *   │  [BLE: Conectado] [MQTT: Conectado]     │  ← StatusIndicators
 *   │                                         │
 *   │          ████ 1234 RPM ████             │  ← Tacómetro digital
 *   │                                         │
 *   │     [↑ Adelante]                        │
 *   │  [← Izq.] [⏹ STOP] [Der. →]           │  ← DirectionControls (D-pad)
 *   │     [↓ Atrás]                           │
 *   │                                         │
 *   │  Velocidad: 128 / 255                   │
 *   │  [════════════════|══════════] Slider   │  ← SpeedControl
 *   │                                         │
 *   │  [ESCANEAR BLE] / [DESCONECTAR]         │
 *   └─────────────────────────────────────────┘
 *
 * Permisos BLE manejados con Accompanist (solicitados antes de escanear).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun GatewayScreen(
    viewModel: GatewayViewModel = viewModel()
) {
    // ── Collect StateFlows ────────────────────────────────────────────────────
    val bleState   by viewModel.bleState.collectAsStateWithLifecycle()
    val mqttState  by viewModel.mqttState.collectAsStateWithLifecycle()
    val currentRpm by viewModel.currentRpm.collectAsStateWithLifecycle()
    val speed      by viewModel.speed.collectAsStateWithLifecycle()

    // ── Permisos BLE dinámicos ────────────────────────────────────────────────
    // Para Android 12+ necesitamos BLUETOOTH_SCAN y BLUETOOTH_CONNECT.
    // Para Android 11 y anteriores, usamos ACCESS_FINE_LOCATION.
    val blePermissions = rememberMultiplePermissionsState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    )

    val isConnected = bleState == BleConnectionState.CONNECTED

    // ── Layout ────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "IoT Edge Gateway",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── 1. Indicadores de estado ──────────────────────────────────────
            ConnectionStatusRow(
                bleState = bleState,
                mqttState = mqttState,
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            // ── 2. Tacómetro digital ──────────────────────────────────────────
            TachoDisplay(rpm = currentRpm)

            Divider()

            // ── 3. Controles D-pad ────────────────────────────────────────────
            DirectionControls(
                onForward  = viewModel::moveForward,
                onBackward = viewModel::moveBackward,
                onLeft     = viewModel::moveLeft,
                onRight    = viewModel::moveRight,
                onStop     = viewModel::stop,
                enabled    = isConnected
            )

            Divider()

            // ── 4. Control de velocidad ───────────────────────────────────────
            SpeedControl(
                speed = speed,
                onSpeedChange = viewModel::updateSpeed,
                enabled = isConnected
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── 5. Botón BLE scan / disconnect ────────────────────────────────
            BleConnectionButton(
                bleState = bleState,
                onScan = {
                    if (blePermissions.allPermissionsGranted) {
                        viewModel.startBleScan()
                    } else {
                        blePermissions.launchMultiplePermissionRequest()
                    }
                },
                onDisconnect = viewModel::disconnectBle
            )

            // Mensaje si permisos fueron denegados permanentemente
            if (blePermissions.shouldShowRationale) {
                Text(
                    text = "⚠️ Se requieren permisos de Bluetooth para conectar al ESP32.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Sub-Composables
// ────────────────────────────────────────────────────────────────────────────

/**
 * Tacómetro digital — muestra las RPM con tipografía grande y destacada.
 */
@Composable
private fun TachoDisplay(rpm: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TACÓMETRO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$rpm",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "RPM",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Control deslizante de velocidad (0..255).
 *
 * El valor del Slider es float (0f..1f) que se mapea a Int (0..255)
 * para coincidir con el rango del byte de velocidad del protocolo BLE.
 */
@Composable
private fun SpeedControl(
    speed         : Int,
    onSpeedChange : (Int) -> Unit,
    enabled       : Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Velocidad",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$speed / 255",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = speed / 255f,
            onValueChange = { fraction ->
                onSpeedChange((fraction * 255).toInt())
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Botón de conexión BLE con comportamiento adaptativo:
 *   - DISCONNECTED → muestra "Buscar ESP32"
 *   - SCANNING / CONNECTING / DISCOVERING → muestra "Buscando..." (deshabilitado)
 *   - CONNECTED → muestra "Desconectar"
 */
@Composable
private fun BleConnectionButton(
    bleState    : BleConnectionState,
    onScan      : () -> Unit,
    onDisconnect: () -> Unit
) {
    when (bleState) {
        BleConnectionState.CONNECTED -> {
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Desconectar BLE")
            }
        }
        BleConnectionState.SCANNING,
        BleConnectionState.CONNECTING,
        BleConnectionState.DISCOVERING -> {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buscando ESP32...")
            }
        }
        BleConnectionState.DISCONNECTED -> {
            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔍  Buscar ESP32 (BLE)")
            }
        }
    }
}