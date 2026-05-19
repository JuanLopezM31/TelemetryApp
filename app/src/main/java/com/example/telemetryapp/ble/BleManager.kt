package com.example.telemetryapp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.telemetryapp.ble.BleConstants.CCCD_UUID
import com.example.telemetryapp.ble.BleConstants.CONTROL_CHAR_UUID
import com.example.telemetryapp.ble.BleConstants.GATT_CONNECT_TIMEOUT_MS
import com.example.telemetryapp.ble.BleConstants.SCAN_PERIOD_MS
import com.example.telemetryapp.ble.BleConstants.SERVICE_UUID
import com.example.telemetryapp.ble.BleConstants.TELEMETRY_CHAR_UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    private val TAG = "BleManager"

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter get() = bluetoothManager.adapter
    private val bleScanner get() = bluetoothAdapter?.bluetoothLeScanner

    // ── Estado observable ────────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _rpmFlow = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    val rpmFlow: SharedFlow<Int> = _rpmFlow.asSharedFlow()

    // ── Referencias GATT ─────────────────────────────────────────────────────
    private var gatt: BluetoothGatt? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null

    private val managerScope = CoroutineScope(Dispatchers.IO + Job())
    private var scanJob: Job? = null

    // ────────────────────────────────────────────────────────────────────────
    // ESCANEO
    // ────────────────────────────────────────────────────────────────────────

    fun startScan() {
        if (_connectionState.value == BleConnectionState.CONNECTED ||
            _connectionState.value == BleConnectionState.CONNECTING) return

        _connectionState.value = BleConnectionState.SCANNING
        Log.d(TAG, "Iniciando escaneo BLE...")

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanJob = managerScope.launch {
            withTimeoutOrNull(SCAN_PERIOD_MS) {
                bleScanner?.startScan(listOf(filter), settings, scanCallback)
                delay(SCAN_PERIOD_MS)
            }
            if (_connectionState.value == BleConnectionState.SCANNING) {
                bleScanner?.stopScan(scanCallback)
                _connectionState.value = BleConnectionState.DISCONNECTED
                Log.w(TAG, "Timeout de escaneo: ESP32 no encontrado")
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        bleScanner?.stopScan(scanCallback)
        if (_connectionState.value == BleConnectionState.SCANNING) {
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // SCAN CALLBACK
    // ────────────────────────────────────────────────────────────────────────

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.i(TAG, "ESP32 encontrado: ${result.device.address} — conectando...")
            bleScanner?.stopScan(this)
            scanJob?.cancel()
            connectToDevice(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Escaneo BLE fallido. Código: $errorCode")
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // CONEXIÓN GATT
    // ────────────────────────────────────────────────────────────────────────

    private fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = BleConnectionState.CONNECTING

        // TRANSPORT_LE fuerza BLE y evita intentos por BT clásico
        gatt = device.connectGatt(
            context,
            false,          // autoConnect = false → conexión directa
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
        Log.d(TAG, "connectGatt() llamado sobre ${device.address}")

        // Timeout manual de conexión
        managerScope.launch {
            delay(GATT_CONNECT_TIMEOUT_MS)
            if (_connectionState.value == BleConnectionState.CONNECTING) {
                Log.w(TAG, "Timeout de conexión GATT. Abortando.")
                disconnect()
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // GATT CALLBACK
    // ────────────────────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            Log.d(TAG, "onConnectionStateChange: status=$status newState=$newState")

            when {
                status != BluetoothGatt.GATT_SUCCESS -> {
                    Log.e(TAG, "GATT error status=$status. Desconectando.")
                    handleDisconnection()
                }
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT conectado. Descubriendo servicios...")
                    _connectionState.value = BleConnectionState.DISCOVERING
                    // Pequeño delay recomendado antes de discoverServices
                    // en algunos dispositivos Android para estabilizar la conexión
                    managerScope.launch {
                        delay(600)
                        gatt.discoverServices()
                    }
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT desconectado.")
                    handleDisconnection()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "discoverServices falló. Status: $status")
                handleDisconnection()
                return
            }

            Log.d(TAG, "Servicios descubiertos: ${gatt.services.map { it.uuid }}")

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Servicio principal NO encontrado en el ESP32.")
                handleDisconnection()
                return
            }
            Log.i(TAG, "✅ Servicio principal encontrado.")

            // Característica de CONTROL
            controlCharacteristic = service.getCharacteristic(CONTROL_CHAR_UUID)
            if (controlCharacteristic != null) {
                Log.i(TAG, "✅ Característica de control lista.")
            } else {
                Log.e(TAG, "❌ Característica de control NO encontrada.")
            }

            // Característica de TELEMETRÍA — activar notificaciones
            val telemetryChar = service.getCharacteristic(TELEMETRY_CHAR_UUID)
            if (telemetryChar != null) {
                Log.i(TAG, "✅ Característica de telemetría encontrada. Activando notify...")
                enableNotifications(gatt, telemetryChar)
            } else {
                Log.e(TAG, "❌ Característica de telemetría NO encontrada.")
            }

            _connectionState.value = BleConnectionState.CONNECTED
            Log.i(TAG, "🚀 BLE listo. Pasarela IoT activa.")
        }

        // API < 33
        @Suppress("DEPRECATION")
        @Deprecated("Deprecated en API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == TELEMETRY_CHAR_UUID) {
                val bytes = characteristic.value ?: return
                processRpmBytes(bytes)
            }
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == TELEMETRY_CHAR_UUID) {
                processRpmBytes(value)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.v(TAG, "Write exitoso en ${characteristic.uuid}")
            } else {
                Log.w(TAG, "Write FALLIDO. Status: $status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "✅ CCCD escrito. Notificaciones de telemetría ACTIVAS.")
            } else {
                Log.e(TAG, "❌ Error escribiendo CCCD. Status: $status")
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Reconstruye RPM desde 2 bytes Big-Endian.
     * rpm = (byte[0] shl 8) or (byte[1] and 0xFF)
     * El ESP32 envía: payload[0] = RPM_HIGH, payload[1] = RPM_LOW
     */
    private fun processRpmBytes(bytes: ByteArray) {
        if (bytes.size < 2) {
            Log.w(TAG, "Payload de telemetría inválido: ${bytes.size} bytes")
            return
        }
        val rpm = (bytes[0].toInt() and 0xFF shl 8) or (bytes[1].toInt() and 0xFF)
        Log.v(TAG, "RPM recibido: $rpm [0x${bytes[0].toHex()}${bytes[1].toHex()}]")
        managerScope.launch { _rpmFlow.emit(rpm) }
    }

    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val success = gatt.setCharacteristicNotification(characteristic, true)
        Log.d(TAG, "setCharacteristicNotification: $success")

        val cccd = characteristic.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            Log.e(TAG, "CCCD descriptor no encontrado. Notificaciones NO activas.")
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    }

    /**
     * Envía un comando de control al ESP32.
     * Debe llamarse desde Dispatchers.IO.
     *
     * @param direction Byte de dirección (BleConstants.DIR_*)
     * @param speed     Velocidad 0x00..0xFF
     */
    suspend fun sendControlCommand(direction: Byte, speed: Byte) {
        val char = controlCharacteristic
        if (char == null) {
            Log.e(TAG, "sendControlCommand: controlCharacteristic es NULL. ¿Conectado?")
            return
        }
        if (_connectionState.value != BleConnectionState.CONNECTED) {
            Log.w(TAG, "sendControlCommand: no conectado. Estado=${_connectionState.value}")
            return
        }

        val payload = byteArrayOf(direction, speed)
        Log.d(TAG, "Enviando → dir=0x${direction.toHex()} speed=0x${speed.toHex()}")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt?.writeCharacteristic(
                char,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            @Suppress("DEPRECATION")
            char.value = payload
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            val result = gatt?.writeCharacteristic(char)
            Log.d(TAG, "writeCharacteristic result: $result")
        }
    }

    private fun handleDisconnection() {
        controlCharacteristic = null
        gatt?.close()
        gatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
        Log.i(TAG, "GATT cerrado y recursos liberados.")
    }

    fun disconnect() {
        gatt?.disconnect()
        handleDisconnection()
    }

    // Extensions
    private fun Byte.toHex() = "%02X".format(this)
}

enum class BleConnectionState {
    DISCONNECTED, SCANNING, CONNECTING, DISCOVERING, CONNECTED
}