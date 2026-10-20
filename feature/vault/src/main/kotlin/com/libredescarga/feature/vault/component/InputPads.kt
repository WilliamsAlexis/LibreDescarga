package com.libredescarga.feature.vault.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.security.CredentialRules
import com.libredescarga.feature.vault.R
import kotlin.math.hypot
import kotlin.math.max

/** Teclado numérico para el PIN: puntos arriba, teclas de 56 dp o más y botón de confirmar. */
@Composable
internal fun PinPad(
    pin: String,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.vault_pin_entered, pin.length)
    Column(
        modifier = modifier.widthIn(max = 320.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(max(CredentialRules.PIN_MIN, pin.length)) { index ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .then(
                            if (index < pin.length) {
                                Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            },
                        ),
                )
            }
        }

        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { digit -> PadKey(label = digit.toString(), enabled = enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PadKey(icon = LibreIcons.Backspace, description = stringResource(R.string.vault_pin_delete), enabled = enabled && pin.isNotEmpty(), onClick = onBackspace)
            PadKey(label = "0", enabled = enabled) { onDigit('0') }
            PadKey(
                icon = LibreIcons.Granted,
                description = stringResource(R.string.vault_pin_confirm),
                enabled = enabled && pin.length >= CredentialRules.PIN_MIN,
                emphasized = true,
                onClick = onSubmit,
            )
        }
    }
}

@Composable
private fun PadKey(
    enabled: Boolean,
    label: String? = null,
    icon: ImageVector? = null,
    description: String? = null,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(68.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (label != null) Text(label, style = MaterialTheme.typography.headlineSmall)
            if (icon != null) Icon(icon, contentDescription = description)
        }
    }
}

/** Patrón de 3 × 3 puntos: se dibuja arrastrando el dedo y se envía al soltar. */
@Composable
internal fun PatternPad(
    enabled: Boolean,
    onComplete: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodes = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentOnComplete by rememberUpdatedState(onComplete)
    val primary = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outline

    fun center(index: Int): Offset {
        val cell = size.width / 3f
        return Offset((index % 3 + 0.5f) * cell, (index / 3 + 0.5f) * cell)
    }

    fun nodeAt(position: Offset): Int? {
        val cell = size.width / 3f
        return (0 until CredentialRules.PATTERN_NODES).firstOrNull { hypot(position.x - center(it).x, position.y - center(it).y) < cell * 0.32f }
    }

    fun touch(position: Offset) {
        finger = position
        nodeAt(position)?.takeIf { it !in nodes }?.let(nodes::add)
    }

    val description = stringResource(R.string.vault_pattern_hint)
    Box(
        modifier = modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { size = it }
            .semantics { contentDescription = description }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        nodes.clear()
                        touch(offset)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        touch(change.position)
                    },
                    onDragEnd = {
                        val result = nodes.toList()
                        nodes.clear()
                        finger = null
                        if (result.isNotEmpty()) currentOnComplete(result)
                    },
                    onDragCancel = {
                        nodes.clear()
                        finger = null
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cell = this.size.width / 3f
            val selected = nodes.toList()
            // Líneas entre los puntos elegidos y hasta el dedo.
            selected.zipWithNext().forEach { (a, b) ->
                drawLine(primary, center(a), center(b), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            }
            val last = selected.lastOrNull()
            val tip = finger
            if (last != null && tip != null) {
                drawLine(primary.copy(alpha = 0.5f), center(last), tip, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            }
            repeat(CredentialRules.PATTERN_NODES) { index ->
                val isSelected = index in selected
                drawCircle(
                    color = if (isSelected) primary else idle,
                    radius = if (isSelected) cell * 0.14f else cell * 0.09f,
                    center = center(index),
                )
                if (isSelected) {
                    drawCircle(primary.copy(alpha = 0.25f), radius = cell * 0.26f, center = center(index), style = Stroke(width = 3.dp.toPx()))
                }
            }
        }
    }
}
