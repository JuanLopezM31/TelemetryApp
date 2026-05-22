# TelemetryApp — Documentación Frontend (UI Layer)

*Proyecto:* IoT Edge Gateway — Carro RC con BLE + MQTT  
*Plataforma:* Android (API 26+)  
*UI Framework:* Jetpack Compose + Material 3  
*Arquitectura:* MVVM  

---

## Tabla de Contenidos

1. [Visión General](#1-visión-general)
2. [Estructura de Archivos](#2-estructura-de-archivos)
3. [Arquitectura UI](#3-arquitectura-ui)
4. [Pantalla Principal — GatewayScreen](#4-pantalla-principal--gatewayscreen)
5. [Componentes](#5-componentes)
   - [ConnectionStatusRow](#51-connectionstatusrow)
   - [TachoDisplay](#52-tachodisplay)
   - [DirectionControls](#53-directioncontrols)
   - [SpeedControl](#54-speedcontrol)
   - [BleConnectionButton](#55-bleconnectionbutton)
6. [Tema y Estilos](#6-tema-y-estilos)
7. [Gestión de Permisos en la UI](#7-gestión-de-permisos-en-la-ui)
8. [Flujo de Datos hacia la UI](#8-flujo-de-datos-hacia-la-ui)
9. [Interacción Usuario → ViewModel](#9-interacción-usuario--viewmodel)
10. [Estados de la UI](#10-estados-de-la-ui)
11. [Guía de Extensión](#11-guía-de-extensión)

---

## 1. Visión General

La capa UI de TelemetryApp es una *pantalla única* construida con Jetpack Compose que actúa como panel de control de un vehículo RC. Cumple tres funciones simultáneas:


┌──────────────────────────────────────────────────────────┐
│  FUNCIÓN 1: MONITOR                                       │
│  Muestra en tiempo real el estado de conexión BLE/MQTT    │
│  y las RPM recibidas del ESP32 vía notificaciones GATT.   │
├──────────────────────────────────────────────────────────┤
│  FUNCIÓN 2: CONTROL                                       │
│  Envía comandos de dirección y velocidad al ESP32 via BLE │
│  mientras el usuario mantiene presionado un botón.        │
├──────────────────────────────────────────────────────────┤
│  FUNCIÓN 3: GATEWAY                                       │
│  Es transparente al usuario, pero cada RPM recibida se    │
│  publica automáticamente en el broker MQTT configurado.   │
└──────────────────────────────────────────────────────────┘


### Layout visual de la pantalla


┌─────────────────────────────────┐
│  🔵 IoT Edge Gateway            │  ← TopBar
├─────────────────────────────────┤
│  [BLE: Conectado] [MQTT: ✓]     │  ← ConnectionStatusRow
│  ─────────────────────────────  │
│                                 │
│           TACÓMETRO             │
│         ┌──────────┐            │
│         │  1 2 4 8  │            │
│         │   RPM    │            │
│         └──────────┘            │
│  ─────────────────────────────  │
│            [↑ Adelante]         │
│    [← Izq.] [⏹️ STOP] [Der. →]  │  ← D-pad
│            [↓ Atrás]            │
│  ─────────────────────────────  │
│  Velocidad              128/255 │
│  [══════════════|═══════]       │  ← Slider
│                                 │
│  [🔍 Buscar ESP32 (BLE)]        │  ← Botón de conexión
└─────────────────────────────────┘


---

## 2. Estructura de Archivos


app/src/main/java/com/example/telemetryapp/
│
├── MainActivity.kt                        # Entry point, setContent()
│
└── ui/
    ├── theme/
    │   └── Theme.kt                       # MaterialTheme, colores claro/oscuro
    │
    ├── screens/
    │   └── GatewayScreen.kt               # Pantalla principal (Scaffold completo)
    │
    └── components/
        ├── StatusIndicators.kt            # Badges de conexión BLE y MQTT
        └── DirectionControls.kt           # D-pad y botón STOP


### Responsabilidad de cada archivo

| Archivo | Responsabilidad |
|---|---|
| MainActivity.kt | Instanciar el Compose tree. No tiene lógica. |
| Theme.kt | Definir la paleta de colores y el @Composable de tema. |
| GatewayScreen.kt | Scaffold, sub-composables locales (Tacho, Slider, BleButton), wiring con ViewModel. |
| StatusIndicators.kt | Renderizar los dos badges de estado. Sin lógica de negocio. |
| DirectionControls.kt | D-pad con pointerInput para press/release. Sin lógica de negocio. |

---

## 3. Arquitectura UI

La UI sigue el patrón *Unidirectional Data Flow (UDF)* estricto:


ViewModel (fuente de verdad)
    │
    │  StateFlow<T>  (solo lectura)
    ▼
GatewayScreen  ─── collectAsStateWithLifecycle() ───▶️  recomposición automática
    │
    │  Callbacks lambda  (eventos del usuario)
    ▼
ViewModel.métodoDeAcción()
    │
    │  launch(Dispatchers.IO)
    ▼
BleManager / MqttManager


### Regla fundamental

> *La UI nunca modifica el estado directamente.*  
> Solo llama métodos del ViewModel. El ViewModel actualiza los StateFlow. Compose recompone.

### ¿Por qué collectAsStateWithLifecycle?

Se usa en lugar de collectAsState() porque:
- Cancela automáticamente la suscripción cuando la Activity va a background (onStop).
- Evita actualizaciones de UI innecesarias cuando la app no está visible.
- Es el patrón recomendado por Google desde Lifecycle 2.6+.

kotlin
// En GatewayScreen.kt
val bleState   by viewModel.bleState.collectAsStateWithLifecycle()
val mqttState  by viewModel.mqttState.collectAsStateWithLifecycle()
val currentRpm by viewModel.currentRpm.collectAsStateWithLifecycle()
val speed      by viewModel.speed.collectAsStateWithLifecycle()


---

## 4. Pantalla Principal — GatewayScreen

*Archivo:* ui/screens/GatewayScreen.kt

### Firma

kotlin
@Composable
fun GatewayScreen(viewModel: GatewayViewModel = viewModel())


El viewModel() por defecto permite que Compose gestione el ciclo de vida del ViewModel correctamente. En tests, se puede inyectar un ViewModel falso.

### Estructura del Scaffold

kotlin
Scaffold(
    topBar = { TopAppBar(title = { Text("IoT Edge Gateway") }) }
) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusRow(...)   // 1
        Divider()
        TachoDisplay(...)          // 2
        Divider()
        DirectionControls(...)     // 3
        Divider()
        SpeedControl(...)          // 4
        Spacer(Modifier.weight(1f))
        BleConnectionButton(...)   // 5
    }
}


### Sub-composables locales (privados en GatewayScreen.kt)

Estos composables son private porque solo tienen sentido dentro de esta pantalla:

| Composable | Es privado porque... |
|---|---|
| TachoDisplay | Diseño específico para mostrar RPM, no reutilizable |
| SpeedControl | Wrapper de Slider con etiquetas contextuales |
| BleConnectionButton | Lógica de estados específica de la app |

Los componentes en ui/components/ son públicos porque podrían reutilizarse en otras pantallas.

---

## 5. Componentes

### 5.1 ConnectionStatusRow

*Archivo:* ui/components/StatusIndicators.kt

Muestra el estado de BLE y MQTT como dos badges lado a lado.

#### Uso

kotlin
ConnectionStatusRow(
    bleState  = bleState,    // BleConnectionState
    mqttState = mqttState,   // MqttConnectionState
    modifier  = Modifier.fillMaxWidth()
)


#### Apariencia por estado

| Estado | Color del badge | Texto |
|---|---|---|
| DISCONNECTED | 🔴 Rojo #F44336 | "Desconectado" |
| SCANNING | 🟡 Ámbar #FFC107 | "Escaneando..." |
| CONNECTING | 🟡 Ámbar #FFC107 | "Conectando..." |
| DISCOVERING | 🟡 Ámbar #FFC107 | "Configurando..." |
| CONNECTED | 🟢 Verde #4CAF50 | "Conectado" |

#### Estructura interna


ConnectionStatusRow
├── StatusBadge("BLE", bleState.toDisplayString(), bleState.toColor())
└── StatusBadge("MQTT", mqttState.toDisplayString(), mqttState.toColor())

StatusBadge
└── Surface (RoundedCornerShape 20dp, color.alpha 15%)
    └── Row
        ├── Box (8dp círculo, color sólido)  ← punto indicador
        └── Text ("BLE: Conectado")


#### Extensiones de mapeo (privadas)

kotlin
private fun BleConnectionState.toDisplayString(): String
private fun BleConnectionState.toColor(): Color
private fun MqttConnectionState.toDisplayString(): String
private fun MqttConnectionState.toColor(): Color


---

### 5.2 TachoDisplay

*Archivo:* ui/screens/GatewayScreen.kt (privado)

Muestra las RPM actuales con tipografía grande.

#### Uso

kotlin
TachoDisplay(rpm = currentRpm)  // Int, actualizado en tiempo real


#### Comportamiento

- Se recompone automáticamente cada vez que currentRpm cambia.
- Las notificaciones del ESP32 llegan cada *250ms* (INTERVALO_TELEMETRIA_MS).
- En modo simulación, el ESP32 genera RPM proporcionales a la velocidad PWM + variación aleatoria de ±20.
- Muestra 0 cuando velocidadActual == 0 o direccionActual == STOP.

#### Jerarquía visual


Card (surfaceVariant background)
└── Column (centrado)
    ├── Text "TACÓMETRO" (labelSmall, letterSpacing 3sp)
    ├── Spacer 4dp
    ├── Text "$rpm" (64sp, Bold, primary color)    ← número grande
    └── Text "RPM" (titleMedium, onSurfaceVariant)


---

### 5.3 DirectionControls

*Archivo:* ui/components/DirectionControls.kt

El componente más crítico de la app. Implementa el D-pad con comportamiento *press-and-hold*.

#### Uso

kotlin
DirectionControls(
    onForward  = viewModel::moveForward,
    onBackward = viewModel::moveBackward,
    onLeft     = viewModel::moveLeft,
    onRight    = viewModel::moveRight,
    onStop     = viewModel::stop,
    enabled    = isConnected   // Boolean: false = botones grises
)


#### Comportamiento press/release

> *Regla de oro:* Al presionar → envía el comando. Al soltar → envía STOP automáticamente.

Esto es crítico para alimentar el *watchdog del ESP32* (timeout de 1500ms). Si la app solo enviara el comando una vez al presionar, el ESP32 detendría los motores al cabo de 1.5 segundos.

*Solución implementada:* El ViewModel tiene un commandLoopJob que reenvía el comando cada *500ms* mientras el botón está presionado:


Usuario presiona ↑           Usuario suelta ↑
    │                              │
    ▼                              ▼
onPress() → startCommandLoop()   awaitRelease() → onRelease() → stop()
    │                                                │
    │  loop cada 500ms:                              │
    │  BLE Write [0x46, speed]                       │  BLE Write [0x53, 0x00]
    │  BLE Write [0x46, speed]                       │
    │  BLE Write [0x46, speed]  ────────────────────▶️│


#### Implementación con pointerInput

La clave técnica es usar detectTapGestures con el lambda onPress que tiene acceso a awaitRelease():

kotlin
Modifier.pointerInput(enabled) {
    if (!enabled) return@pointerInput
    detectTapGestures(
        onPress = { _ ->
            pressed = true
            onPress()          // → viewModel.moveForward()
            try {
                awaitRelease() // Suspende hasta que el dedo se levanta
            } finally {
                pressed = false
                onRelease()    // → viewModel.stop()  (siempre se ejecuta)
            }
        }
    )
}


> *¿Por qué finally?* Garantiza que stop() se envía incluso si la coroutine es cancelada (por ejemplo, si la app va a background mientras el botón está presionado).

#### ¿Por qué NO se usa el composable Button?

El composable Button de Material 3 tiene su propio onClick interno que *compite* con pointerInput por los gestos táctiles. Cuando ambos están presentes en el mismo elemento, el onClick del Button consume el evento antes de que llegue al pointerInput, lo que hace que el press/release nunca se dispare correctamente.

*Solución:* Los botones son Box + clip + background + pointerInput puros, sin usar el composable Button:

kotlin
Box(
    modifier = Modifier
        .size(72.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(bgColor)
        .pointerInput(enabled) { ... }   // Sin Button encima
)


#### Layout del D-pad


         [↑ Adelante]
              72dp
[← Izq.]  [⏹️ STOP]  [Der. →]    ← Row, gap 8dp
              72dp
         [↓ Atrás]


El botón STOP es rojo (#F44336) y usa onTap en lugar de onPress, ya que STOP siempre es instantáneo (no necesita repetición).

#### Estado enabled

Cuando enabled = false (BLE desconectado):
- pointerInput retorna inmediatamente (if (!enabled) return@pointerInput).
- El color de fondo cambia a Color.Gray.copy(alpha = 0.3f).
- No hay feedback visual de press.

---

### 5.4 SpeedControl

*Archivo:* ui/screens/GatewayScreen.kt (privado)

Slider de 0 a 255 que controla el byte de velocidad del protocolo BLE.

#### Uso

kotlin
SpeedControl(
    speed         = speed,              // Int 0..255 del StateFlow
    onSpeedChange = viewModel::updateSpeed,
    enabled       = isConnected
)


#### Mapeo Slider ↔️ Protocolo


Slider value (Float)  →  speed (Int)  →  BLE Byte 1
    0.0f              →      0        →    0x00  (motor detenido)
    0.5f              →    127        →    0x7F  (velocidad media)
    1.0f              →    255        →    0xFF  (velocidad máxima)


kotlin
// Slider → ViewModel
onValueChange = { fraction -> onSpeedChange((fraction * 255).toInt()) }

// ViewModel → Slider
value = speed / 255f


#### Valor inicial

El ViewModel inicializa _speed con 128 (50%) para que la primera prueba de conexión muestre RPM visibles sin necesidad de mover el slider.

---

### 5.5 BleConnectionButton

*Archivo:* ui/screens/GatewayScreen.kt (privado)

Botón adaptativo que cambia según el estado BLE actual.

#### Estados del botón

kotlin
when (bleState) {
    CONNECTED    → Button("Desconectar BLE")      onClick: viewModel::disconnectBle
    SCANNING,
    CONNECTING,
    DISCOVERING  → Button("Buscando ESP32...",     enabled = false)
    DISCONNECTED → Button("🔍 Buscar ESP32 (BLE)") onClick: { verificarPermisos() }
}


#### Flujo de permisos

Cuando el usuario pulsa "Buscar ESP32", la app verifica los permisos *antes* de llamar al ViewModel:

kotlin
onClick = {
    if (blePermissions.allPermissionsGranted) {
        viewModel.startBleScan()     // Permisos OK → escanear
    } else {
        blePermissions.launchMultiplePermissionRequest()  // Pedir permisos
    }
}


Si el usuario deniega permanentemente los permisos, se muestra un mensaje explicativo debajo del botón.

---

## 6. Tema y Estilos

*Archivo:* ui/theme/Theme.kt

### Paleta de colores

kotlin
private val DarkColorScheme = darkColorScheme(
    primary   = Color(0xFF82B1FF),   // Azul claro
    secondary = Color(0xFF80CBC4),   // Verde agua
    tertiary  = Color(0xFFCFD8DC)    // Gris azulado
)

private val LightColorScheme = lightColorScheme(
    primary   = Color(0xFF1565C0),   // Azul oscuro
    secondary = Color(0xFF00796B),   // Verde oscuro
    tertiary  = Color(0xFF455A64)    // Gris pizarra
)


### Uso del tema

kotlin
// MainActivity.kt
IoTEdgeGatewayTheme {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        GatewayScreen()
    }
}


### Modo oscuro

El tema detecta automáticamente el modo del sistema con isSystemInDarkTheme(). No hay toggle manual en la app actualmente.

### Colores usados en los componentes

| Elemento | Token de color |
|---|---|
| Número de RPM | MaterialTheme.colorScheme.primary |
| Fondo tacómetro | MaterialTheme.colorScheme.surfaceVariant |
| Texto secundario | MaterialTheme.colorScheme.onSurfaceVariant |
| Botones D-pad | MaterialTheme.colorScheme.primary |
| Botón STOP | Color(0xFFF44336) (rojo fijo, semántico) |
| Badge conectado | Color(0xFF4CAF50) (verde fijo, semántico) |
| Badge desconectado | Color(0xFFF44336) (rojo fijo, semántico) |
| Badge conectando | Color(0xFFFFC107) (ámbar fijo, semántico) |

> Los colores de estado (rojo/verde/ámbar) son *fijos* (no usan tokens de Material) porque representan semántica universal (stop/go/wait) que no debe cambiar con el tema.

---

## 7. Gestión de Permisos en la UI

*Librería:* com.google.accompanist:accompanist-permissions:0.36.0

### Permisos requeridos según versión Android

kotlin
val blePermissions = rememberMultiplePermissionsState(
    permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ (API 31+)
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        // Android 11 y anteriores (API ≤ 30)
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
)


### Estados de permisos

| blePermissions.allPermissionsGranted | blePermissions.shouldShowRationale | Acción |
|---|---|---|
| true | — | Llamar viewModel.startBleScan() directamente |
| false | false | Lanzar el diálogo del sistema con launchMultiplePermissionRequest() |
| false | true | Mostrar mensaje explicativo en la UI |

### ¿Por qué shouldShowRationale?

Android devuelve shouldShowRationale = true cuando el usuario ya denegó el permiso al menos una vez. En ese caso, se muestra un texto en la pantalla explicando por qué la app necesita el permiso, antes de volver a pedirlo.

---

## 8. Flujo de Datos hacia la UI

### RPM: ESP32 → UI


ESP32 GATT Notify (cada 250ms)
    │
    │  ByteArray(2): [RPM_HIGH, RPM_LOW]
    ▼
BleManager.processRpmBytes()
    │
    │  rpm = (byte[0] and 0xFF shl 8) or (byte[1] and 0xFF)
    │  _rpmFlow.emit(rpm)                    [Dispatchers.IO]
    ▼
GatewayViewModel.observeRpmTelemetry()
    │
    ├──▶️  _currentRpm.value = rpm            → Recomposición de TachoDisplay
    │
    └──▶️  mqttManager.publishRpm(rpm)        → Publicación MQTT


### Estado BLE: BleManager → UI


BluetoothGattCallback.onConnectionStateChange()
    │
    │  _connectionState.value = BleConnectionState.CONNECTED
    ▼
bleManager.connectionState  (StateFlow)
    │
    │  collectAsStateWithLifecycle()
    ▼
GatewayScreen
    ├──▶️  ConnectionStatusRow (badge color)
    ├──▶️  DirectionControls (enabled/disabled)
    ├──▶️  SpeedControl (enabled/disabled)
    └──▶️  BleConnectionButton (texto del botón)


---

## 9. Interacción Usuario → ViewModel

### Mapa completo de acciones

| Evento UI | Método del ViewModel | Acción resultante |
|---|---|---|
| Tap "Buscar ESP32" | startBleScan() | Inicia escaneo BLE en Dispatchers.IO |
| Tap "Desconectar" | disconnectBle() | Cancela loop, desconecta GATT |
| Press ↑ | moveForward() | Inicia loop: BLE Write [0x46, speed] cada 500ms |
| Press ↓ | moveBackward() | Inicia loop: BLE Write [0x42, speed] cada 500ms |
| Press ← | moveLeft() | Inicia loop: BLE Write [0x4C, speed] cada 500ms |
| Press → | moveRight() | Inicia loop: BLE Write [0x52, speed] cada 500ms |
| Release cualquier botón | stop() | Cancela loop + BLE Write [0x53, 0x00] |
| Tap STOP | stop() | Cancela loop + BLE Write [0x53, 0x00] |
| Mover Slider | updateSpeed(value) | Actualiza _speed.value (0..255) |

### Comandos BLE del protocolo

| Byte 0 (Dirección) | Hex | Byte 1 (Velocidad) |
|---|---|---|
| Forward (Adelante) | 0x46 | 0..255 |
| Backward (Atrás) | 0x42 | 0..255 |
| Left (Izquierda) | 0x4C | 0..255 |
| Right (Derecha) | 0x52 | 0..255 |
| Stop | 0x53 | 0x00 |

---

## 10. Estados de la UI

### Diagrama de estados completo


                    ┌─────────────────────────────────────────────┐
                    │           ESTADOS BLE                        │
                    └─────────────────────────────────────────────┘

     [App abre]
         │
         ▼
   DISCONNECTED ──── tap "Buscar" ────▶️ SCANNING
         ▲                                 │
         │                         dispositivo encontrado
         │                                 │
         │                                 ▼
         │                          CONNECTING
         │                                 │
         │                         GATT conectado
         │                                 │
         │                                 ▼
         │                          DISCOVERING
         │                                 │
         │                      servicios descubiertos
         │                                 │
         │                                 ▼
         │◀️──── tap "Desconectar" ────CONNECTED ◀️─┐
         │                                 │       │
         │◀️──── error GATT / timeout ──────┘       │
                                                   │
                                           auto-reconecta (futuro)


### Combinaciones de estado posibles

| BLE | MQTT | Situación |
|---|---|---|
| DISCONNECTED | DISCONNECTED | App recién abierta, sin WiFi |
| DISCONNECTED | CONNECTED | MQTT conectado, BLE no escaneado aún |
| CONNECTED | CONNECTED | ✅ Estado operativo normal |
| CONNECTED | DISCONNECTED | ESP32 conectado, broker MQTT caído |
| SCANNING | CONNECTING | Transición (ámbar en ambos badges posible) |

---

## 11. Guía de Extensión

### Agregar un nuevo indicador en la UI (ej: temperatura del motor)

1. Agregar StateFlow<Int> al ViewModel:
kotlin
private val _temperature = MutableStateFlow(0)
val temperature: StateFlow<Int> = _temperature.asStateFlow()


2. Emitir desde el flujo BLE si el ESP32 lo envía en un nuevo tópico/característica, o calcularlo en el ViewModel.

3. Collectar en GatewayScreen.kt:
kotlin
val temperature by viewModel.temperature.collectAsStateWithLifecycle()


4. Crear el composable de visualización y añadirlo al Column.

---

### Agregar un botón de Healthcheck en la UI

El ViewModel ya expone sendHealthcheck(). Solo falta un botón en GatewayScreen:

kotlin
Button(
    onClick = viewModel::sendHealthcheck,
    enabled = mqttState == MqttConnectionState.CONNECTED
) {
    Text("Ping MQTT")
}


---

### Cambiar el intervalo de reenvío de comandos

El watchdog del ESP32 está configurado en 1500ms. El intervalo de reenvío de la app está en el ViewModel:

kotlin
// GatewayViewModel.kt
private val COMMAND_REPEAT_MS = 500L   // ← cambiar aquí


> *Regla:* COMMAND_REPEAT_MS debe ser siempre menor a WATCHDOG_TIMEOUT_MS / 2 para garantizar al menos 2 comandos antes del timeout.

---

### Agregar soporte para landscape

Actualmente la UI solo está optimizada para portrait. Para landscape, agregar en GatewayScreen.kt:

kotlin
val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

if (isLandscape) {
    Row { /* Tacho a la izquierda, D-pad a la derecha */ }
} else {
    Column { /* Layout actual */ }
}


---

### Reemplazar el Slider por un joystick virtual

El SpeedControl actual controla solo la magnitud. Para un joystick 2D que controle dirección + velocidad simultáneamente, reemplazar DirectionControls + SpeedControl por un composable personalizado con pointerInput que detecte la posición relativa del dedo y calcule:

kotlin
val angle = atan2(dy, dx)
val magnitude = sqrt(dx*dx + dy*dy).coerceIn(0f, maxRadius)
val direction = angleToCommand(angle)   // 0x46, 0x42, 0x4C, 0x52
val speed = (magnitude / maxRadius * 255).toInt()


---

## Dependencias de la capa UI

kotlin
// Compose BOM (gestiona versiones de todos los artefactos Compose)
val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
implementation(composeBom)
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")

// ViewModel + ciclo de vida
implementation("androidx.activity:activity-compose:1.9.3")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")  // collectAsStateWithLifecycle

// Permisos en runtime
implementation("com.google.accompanist:accompanist-permissions:0.36.0")


---

Documentación generada para TelemetryApp v1.0 — Equipo de Desarrollo
