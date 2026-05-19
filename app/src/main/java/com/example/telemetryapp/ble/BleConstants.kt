package com.example.telemetryapp.ble


import java.util.UUID

/**
 * Constantes del protocolo BLE / GATT acordadas con el firmware del ESP32.
 *
 * Perfil GATT del servidor (ESP32):
 * └── Servicio Principal [SERVICE_UUID]
 *     ├── Característica de Control [CONTROL_CHAR_UUID]   → Write
 *     │     Payload: ByteArray(2)
 *     │       byte[0] = Dirección  (0x46='F', 0x42='B', 0x4C='L', 0x52='R', 0x53='S')
 *     │       byte[1] = Velocidad  (0x00..0xFF)
 *     └── Característica de Telemetría [TELEMETRY_CHAR_UUID] → Notify
 *           Payload: ByteArray(2)  → RPM = (byte[0].toInt() shl 8) or (byte[1].toInt() and 0xFF)
 */
object BleConstants {

    /** UUID del servicio GATT principal expuesto por el ESP32 */
    val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")

    /** Característica de escritura → envía comandos de dirección + velocidad */
    val CONTROL_CHAR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    /** Característica de notificación → recibe RPM del encoder del motor */
    val TELEMETRY_CHAR_UUID: UUID = UUID.fromString("1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e")

    /**
     * UUID estándar del descriptor Client Characteristic Configuration (CCCD).
     * Escribir [0x01, 0x00] en este descriptor activa las notificaciones BLE.
     */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Nombre anunciado por el ESP32 (utilizado como filtro de escaneo secundario) */
    const val DEVICE_NAME = "ESP32_CAR"

    // ── Comandos de dirección (byte[0] del payload de control) ──────────────
    const val DIR_FORWARD  : Byte = 0x46   // 'F'
    const val DIR_BACKWARD : Byte = 0x42   // 'B'
    const val DIR_LEFT     : Byte = 0x4C   // 'L'
    const val DIR_RIGHT    : Byte = 0x52   // 'R'
    const val DIR_STOP     : Byte = 0x53   // 'S'

    // ── Timeouts y reintentos ────────────────────────────────────────────────
    const val SCAN_PERIOD_MS         = 10_000L
    const val GATT_CONNECT_TIMEOUT_MS = 8_000L
    const val WRITE_TIMEOUT_MS        = 2_000L
}