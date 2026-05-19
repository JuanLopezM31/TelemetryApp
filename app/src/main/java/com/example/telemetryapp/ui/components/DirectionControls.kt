package com.example.telemetryapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DirectionControls(
    onForward  : () -> Unit,
    onBackward : () -> Unit,
    onLeft     : () -> Unit,
    onRight    : () -> Unit,
    onStop     : () -> Unit,
    enabled    : Boolean = true,
    modifier   : Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DirectionButton(
            icon = Icons.Default.ArrowUpward,
            label = "Adelante",
            color = MaterialTheme.colorScheme.primary,
            onPress = onForward,
            onRelease = onStop,
            enabled = enabled
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DirectionButton(
                icon = Icons.Default.ArrowBack,
                label = "Izq.",
                color = MaterialTheme.colorScheme.primary,
                onPress = onLeft,
                onRelease = onStop,
                enabled = enabled
            )
            // STOP — solo click simple, no necesita press/release
            StopButton(onClick = onStop, enabled = enabled)

            DirectionButton(
                icon = Icons.Default.ArrowForward,
                label = "Der.",
                color = MaterialTheme.colorScheme.primary,
                onPress = onRight,
                onRelease = onStop,
                enabled = enabled
            )
        }
        DirectionButton(
            icon = Icons.Default.ArrowDownward,
            label = "Atrás",
            color = MaterialTheme.colorScheme.primary,
            onPress = onBackward,
            onRelease = onStop,
            enabled = enabled
        )
    }
}

/**
 * Botón direccional implementado con Box + pointerInput puro.
 * NO usa el composable Button para evitar que su onClick interno
 * consuma el evento antes de que llegue a pointerInput.
 */
@Composable
private fun DirectionButton(
    icon      : ImageVector,
    label     : String,
    color     : Color,
    onPress   : () -> Unit,
    onRelease : () -> Unit,
    enabled   : Boolean
) {
    var pressed by remember { mutableStateOf(false) }

    val bgColor = when {
        !enabled -> Color.Gray.copy(alpha = 0.3f)
        pressed  -> color.copy(alpha = 0.6f)
        else     -> color
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { _ ->
                        pressed = true
                        onPress()
                        try {
                            awaitRelease()
                        } finally {
                            pressed = false
                            onRelease()
                        }
                    }
                )
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun StopButton(onClick: () -> Unit, enabled: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Color(0xFFF44336) else Color.Gray.copy(alpha = 0.3f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "STOP",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text("STOP", fontSize = 9.sp, color = Color.White)
        }
    }
}