package com.shadaeiou.stitchcounter.ui.counter

import android.view.SoundEffectConstants
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable as FloatAnimatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shadaeiou.stitchcounter.StitchCounterApp
import com.shadaeiou.stitchcounter.data.notes.NoteItem
import com.shadaeiou.stitchcounter.ui.theme.FlashGreen
import com.shadaeiou.stitchcounter.ui.theme.FlashRed
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAP_DEBOUNCE_MS = 250L
private const val LONG_PRESS_MS = 400L
private const val HINT_DELAY_MS = 200L
private const val MOVE_CANCEL_DP = 10
private const val PULL_TRIGGER_DP = 60
private const val FLASH_MS = 600

@Composable
fun CounterArea(
    count: Int,
    label: String,
    locked: Boolean,
    interactionsEnabled: Boolean,
    backgroundArgb: Long,
    knitPattern: String,
    pinnedNotes: List<NoteItem>,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onLabelChange: (String) -> Unit,
    onPullDown: () -> Unit,
    onReset: () -> Unit,
    onEditPattern: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gesturesActive = !locked && interactionsEnabled
    val haptics = remember { StitchCounterApp.instance.haptics }
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentCount by rememberUpdatedState(count)

    val flash = remember { Animatable(Color.Transparent) }
    val shake = remember { FloatAnimatable(0f) }
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
    val backgroundColor = Color(backgroundArgb.toInt())
    val patternLetter = stitchLetterFor(count, knitPattern)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = shake.value }
                .alpha(if (locked) 0.4f else 1f)
                .drawWithContent {
                    drawContent()
                    if (flash.value.alpha > 0f) drawRect(flash.value)
                }
                .pointerInput(gesturesActive) {
                    if (!gesturesActive) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val startTime = System.currentTimeMillis()
                        val startPos = down.position
                        var hintFired = false
                        var pullTracking = false
                        var pressFired = false

                        while (true) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val nextDeadline: Long = when {
                                pullTracking || pressFired -> Long.MAX_VALUE
                                !hintFired && elapsed < HINT_DELAY_MS -> HINT_DELAY_MS - elapsed
                                elapsed < LONG_PRESS_MS -> LONG_PRESS_MS - elapsed
                                else -> 0L
                            }

                            if (!pullTracking && !pressFired && nextDeadline <= 0L) {
                                if (!hintFired) {
                                    hintFired = true
                                    hintVisible = true
                                    continue
                                }
                                haptics.heavy()
                                if (currentCount <= 0) {
                                    shakeKey++
                                } else {
                                    onDecrement()
                                    scope.launch {
                                        flash.snapTo(FlashRed)
                                        flash.animateTo(Color.Transparent, tween(FLASH_MS))
                                    }
                                }
                                pressFired = true
                                hintVisible = false
                                continue
                            }

                            val event = if (nextDeadline == Long.MAX_VALUE) awaitPointerEvent()
                                        else withTimeoutOrNull(nextDeadline) { awaitPointerEvent() }
                                            ?: continue

                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer == null || !pointer.pressed) {
                                hintVisible = false
                                if (!pressFired && !pullTracking) {
                                    val nowMs = System.currentTimeMillis()
                                    if (nowMs - lastTapAt >= TAP_DEBOUNCE_MS) {
                                        lastTapAt = nowMs
                                        haptics.light()
                                        view.playSoundEffect(SoundEffectConstants.CLICK)
                                        onIncrement()
                                        scope.launch {
                                            flash.snapTo(FlashGreen)
                                            flash.animateTo(Color.Transparent, tween(FLASH_MS))
                                        }
                                    }
                                }
                                break
                            }

                            if (pressFired) continue

                            val dx = pointer.position.x - startPos.x
                            val dy = pointer.position.y - startPos.y

                            if (dy > pullTriggerPx) {
                                hintVisible = false
                                haptics.pull()
                                onPullDown()
                                pressFired = true
                                pullTracking = true
                                continue
                            }

                            val dist = sqrt(dx * dx + dy * dy)
                            if (!pullTracking && dist > moveCancelPx) {
                                val isPullTrajectory = dy > 0f && dy >= abs(dx)
                                if (isPullTrajectory) {
                                    pullTracking = true
                                    hintVisible = false
                                } else {
                                    hintVisible = false
                                    pressFired = true
                                }
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
                    onStartEdit = { if (!locked) labelEditing = true },
                    onCommit = {
                        if (it != label) onLabelChange(it)
                        labelEditing = false
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
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
                    if (patternLetter != null) {
                        Text(
                            text = patternLetter.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            color = if (patternLetter == 'K') Color(0xFF4ADE80) else Color(0xFFF87171),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                }
            }
        }

        // Pinned notes ribbon at the very top of the counter screen.
        if (pinnedNotes.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 64.dp, end = 64.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pinnedNotes.take(3).forEach { note ->
                    Text(
                        note.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Pattern editor button (top-start).
        IconButton(
            onClick = onEditPattern,
            enabled = !locked,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    CircleShape,
                ),
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = "Edit knit pattern",
                tint = Color.White.copy(alpha = 0.85f),
            )
        }

        // Reset button (top-end). Declared last so it sits above the gesture
        // box; clickable consumes the down so the gesture box's
        // awaitFirstDown(requireUnconsumed=true) skips it.
        IconButton(
            onClick = onReset,
            enabled = !locked && interactionsEnabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    CircleShape,
                ),
        ) {
            Icon(
                Icons.Default.RestartAlt,
                contentDescription = "Reset counter",
                tint = Color.White.copy(alpha = 0.85f),
            )
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
        val currentDraft = rememberUpdatedState(draft)
        val commit = rememberUpdatedState(onCommit)
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = LocalTextStyle.current.copy(
                color = Color.White,
                textAlign = TextAlign.Center,
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit.value(currentDraft.value.trim()) }),
            modifier = Modifier
                .padding(bottom = 16.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (!state.isFocused) {
                        // Field lost focus (user tapped away or navigated to
                        // another screen). Commit and exit edit mode so the
                        // keyboard doesn't keep popping back up later.
                        commit.value(currentDraft.value.trim())
                    }
                },
        )
    } else {
        val display = label.ifBlank { "Tap to set label" }
        val color =
            if (label.isBlank()) Color.White.copy(alpha = 0.5f)
            else Color.White
        Text(
            text = display,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .clickable(onClick = onStartEdit),
        )
    }
}

private fun stitchLetterFor(count: Int, pattern: String): Char? {
    val cleaned = pattern.filter { it == 'K' || it == 'P' }
    if (cleaned.isEmpty()) return null
    val safeCount = if (count < 0) 0 else count
    return cleaned[safeCount % cleaned.length]
}
