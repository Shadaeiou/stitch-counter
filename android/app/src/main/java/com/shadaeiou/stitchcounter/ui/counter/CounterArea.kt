package com.shadaeiou.stitchcounter.ui.counter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shadaeiou.stitchcounter.ui.theme.FlashGreen
import com.shadaeiou.stitchcounter.ui.theme.FlashRed
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.sqrt

private const val TAP_DEBOUNCE_MS = 250L
private const val LONG_PRESS_MS = 400L
private const val HINT_DELAY_MS = 200L
private const val MOVE_CANCEL_DP = 10
private const val PULL_TRIGGER_DP = 80
private const val FLASH_MS = 150
private const val SHAKE_MS = 200

@Composable
fun CounterArea(
    count: Int,
    label: String,
    locked: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onLabelChange: (String) -> Unit,
    onPullDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val flash = remember { Animatable(Color.Transparent) }
    val shake = remember { Animatable(0f) }
    var hintVisible by remember { mutableStateOf(false) }
    var labelEditing by remember { mutableStateOf(false) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    var shakeKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        val amplitude = with(density) { 8.dp.toPx() }
        shake.snapTo(0f)
        shake.animateTo(amplitude, tween(40, easing = LinearEasing))
        shake.animateTo(-amplitude, tween(60, easing = LinearEasing))
        shake.animateTo(amplitude, tween(60, easing = LinearEasing))
        shake.animateTo(0f, tween(40, easing = LinearEasing))
    }

    val moveCancelPx = with(density) { MOVE_CANCEL_DP.dp.toPx() }
    val pullTriggerPx = with(density) { PULL_TRIGGER_DP.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer { translationX = shake.value }
            .alpha(if (locked) 0.4f else 1f)
            .drawWithContent {
                drawContent()
                if (flash.value.alpha > 0f) drawRect(flash.value)
            }
            .pointerInput(locked, count) {
                if (locked) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startTime = System.currentTimeMillis()
                    val startPos = down.position
                    var hintFired = false
                    var fired = false

                    while (true) {
                        val now = System.currentTimeMillis() - startTime
                        val nextDeadline = when {
                            !hintFired && now < HINT_DELAY_MS -> HINT_DELAY_MS - now
                            now < LONG_PRESS_MS -> LONG_PRESS_MS - now
                            else -> 0L
                        }

                        if (nextDeadline <= 0L) {
                            if (!hintFired) {
                                hintFired = true
                                hintVisible = true
                                continue
                            }
                            // Long-press fires
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (count <= 0) {
                                shakeKey++
                            } else {
                                onDecrement()
                                scope.launch {
                                    flash.snapTo(FlashRed)
                                    flash.animateTo(Color.Transparent, tween(FLASH_MS))
                                }
                            }
                            fired = true
                            hintVisible = false
                            // Drain remaining events until lift
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.all { !it.pressed }) break
                            }
                            break
                        }

                        val event = withTimeoutOrNull(nextDeadline) { awaitPointerEvent() }
                            ?: continue

                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer == null) {
                            hintVisible = false
                            break
                        }
                        if (!pointer.pressed) {
                            // Lifted before long-press fired → tap
                            hintVisible = false
                            val nowMs = System.currentTimeMillis()
                            if (!fired && nowMs - lastTapAt >= TAP_DEBOUNCE_MS) {
                                lastTapAt = nowMs
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onIncrement()
                                scope.launch {
                                    flash.snapTo(FlashGreen)
                                    flash.animateTo(Color.Transparent, tween(FLASH_MS))
                                }
                            }
                            break
                        }

                        val dx = pointer.position.x - startPos.x
                        val dy = pointer.position.y - startPos.y

                        // Pull-down detection (vertical, downward)
                        if (dy > pullTriggerPx) {
                            hintVisible = false
                            onPullDown()
                            // Drain
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.all { !it.pressed }) break
                            }
                            break
                        }

                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist > moveCancelPx) {
                            hintVisible = false
                            // Movement cancels long-press AND tap (matches design intent)
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.all { !it.pressed }) break
                            }
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp),
        ) {
            LabelEditor(
                label = label,
                editing = labelEditing,
                onStartEdit = { labelEditing = true },
                onCommit = {
                    onLabelChange(it)
                    labelEditing = false
                },
            )
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                if (hintVisible) {
                    Text(
                        "−1",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LabelEditor(
    label: String,
    editing: Boolean,
    onStartEdit: () -> Unit,
    onCommit: (String) -> Unit,
) {
    if (editing) {
        var draft by remember(label) { mutableStateOf(label) }
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit(draft.trim()) }),
            modifier = Modifier.padding(bottom = 12.dp),
        )
    } else {
        val display = label.ifBlank { "Tap to set label" }
        val color = if (label.isBlank())
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.onSurface
        Text(
            text = display,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // Wait for lift, then enter edit mode (avoid stealing counter taps)
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val change = ev.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                if (!moved) onStartEdit()
                                break
                            }
                            val d = change.position - change.previousPosition
                            if (d.getDistance() > 10f) moved = true
                        }
                    }
                },
        )
    }
}

