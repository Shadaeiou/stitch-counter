package com.shadaeiou.stitchcounter.ui.counter

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.SoundEffectConstants
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable as FloatAnimatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadaeiou.stitchcounter.StitchCounterApp
import com.shadaeiou.stitchcounter.data.notes.NoteItem
import com.shadaeiou.stitchcounter.ui.theme.FlashRed
import com.shadaeiou.stitchcounter.ui.theme.FlashWhite
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAP_DEBOUNCE_MS = 80L
private const val LONG_PRESS_MS = 400L
private const val HINT_DELAY_MS = 200L
private const val MOVE_CANCEL_DP = 10
private const val PULL_TRIGGER_DP = 60
private const val FLASH_MS = 600

@Composable
fun CounterArea(
    count: Int,
    locked: Boolean,
    interactionsEnabled: Boolean,
    backgroundArgb: Long,
    knitPattern: String,
    counterView: Int,
    pinnedNotes: List<NoteItem>,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
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

    // ToneGenerator plays a short tone independent of the system "touch
    // sounds" setting. Volume is maxed (100/100) and we use a loud PBX
    // click tone so the click is audible even over media playback.
    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull()
    }
    DisposableEffect(toneGen) {
        onDispose { toneGen?.release() }
    }

    val flash = remember { Animatable(Color.Transparent) }
    val shake = remember { FloatAnimatable(0f) }
    var hintVisible by remember { mutableStateOf(false) }
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
    val patternIndicator = stitchIndicatorFor(count, knitPattern)
    val nextRowIndicator = if (counterView == 1) stitchIndicatorFor(count + 1, knitPattern) else null

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
                                        toneGen?.startTone(ToneGenerator.TONE_CDMA_HIGH_PBX_L, 80)
                                        view.playSoundEffect(SoundEffectConstants.CLICK)
                                        onIncrement()
                                        scope.launch {
                                            flash.snapTo(FlashWhite)
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
            when (counterView) {
                1 -> {
                    // View 2: rows completed (de-emphasised left) + next row (primary right)
                    Row(
                        modifier = Modifier.fillMaxWidth(0.92f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left — rows completed: smaller, 20 % transparent
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = count.toString(),
                                    fontSize = counterFontSize(count.toString().length),
                                    lineHeight = counterFontSize(count.toString().length),
                                    fontWeight = FontWeight.Light,
                                    color = Color.White.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                if (hintVisible) {
                                    Text(
                                        "−1",
                                        fontSize = counterFontSize(count.toString().length),
                                        lineHeight = counterFontSize(count.toString().length),
                                        fontWeight = FontWeight.Light,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            Text(
                                "rows completed",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                        // Right — next row: full opacity, slightly smaller than rows completed
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = (count + 1).toString(),
                                    fontSize = counterFontSize((count + 1).toString().length),
                                    lineHeight = counterFontSize((count + 1).toString().length),
                                    fontWeight = FontWeight.Light,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                if (nextRowIndicator != null) {
                                    val nColor = when {
                                        nextRowIndicator.startsWith('K', ignoreCase = true) -> Color(0xFF4ADE80)
                                        nextRowIndicator.startsWith('P', ignoreCase = true) -> Color(0xFFF87171)
                                        else -> Color(0xFF93C5FD)
                                    }
                                    val nStyle = when {
                                        nextRowIndicator.length <= 3 -> MaterialTheme.typography.headlineMedium
                                        nextRowIndicator.length <= 8 -> MaterialTheme.typography.titleLarge
                                        else -> MaterialTheme.typography.titleMedium
                                    }
                                    Text(
                                        text = nextRowIndicator.take(20),
                                        style = nStyle,
                                        color = nColor,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(
                                "next row",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                else -> {
                    // View 1 (default): count + pattern indicator, "rows completed" label below
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = count.toString(),
                                    fontSize = counterFontSize(count.toString().length),
                                    lineHeight = counterFontSize(count.toString().length),
                                    fontWeight = FontWeight.Light,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                if (hintVisible) {
                                    Text(
                                        "−1",
                                        fontSize = counterFontSize(count.toString().length),
                                        lineHeight = counterFontSize(count.toString().length),
                                        fontWeight = FontWeight.Light,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            if (patternIndicator != null) {
                                val indicatorColor = when {
                                    patternIndicator.startsWith('K', ignoreCase = true) -> Color(0xFF4ADE80)
                                    patternIndicator.startsWith('P', ignoreCase = true) -> Color(0xFFF87171)
                                    else -> Color(0xFF93C5FD)
                                }
                                val indicatorStyle = when {
                                    patternIndicator.length <= 3 -> MaterialTheme.typography.displayMedium
                                    patternIndicator.length <= 8 -> MaterialTheme.typography.headlineMedium
                                    else -> MaterialTheme.typography.titleLarge
                                }
                                Text(
                                    text = patternIndicator.take(20),
                                    style = indicatorStyle,
                                    color = indicatorColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                        Text(
                            "rows completed",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f),
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
                    AutoSizeNoteText(
                        text = note.text,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .fillMaxWidth(),
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

        // Reset button (top-end).
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

// Shrinks fontSize until the text fits within its width/height constraints,
// so a long pinned note becomes readable without overflowing the top ribbon.
@Composable
private fun AutoSizeNoteText(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 28.sp,
    minFontSize: TextUnit = 8.sp,
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    Text(
        text = text,
        modifier = modifier,
        color = Color.White,
        fontSize = fontSize,
        textAlign = TextAlign.Center,
        maxLines = 4,
        softWrap = true,
        onTextLayout = { result ->
            if ((result.didOverflowWidth || result.didOverflowHeight) &&
                fontSize.value > minFontSize.value
            ) {
                val next = (fontSize.value - 1f).coerceAtLeast(minFontSize.value).sp
                if (next.value < fontSize.value) fontSize = next
            }
        },
    )
}

private fun counterFontSize(digitCount: Int): TextUnit = when {
    digitCount <= 1 -> 180.sp
    digitCount <= 2 -> 140.sp
    digitCount <= 3 -> 100.sp
    else -> 75.sp
}

private const val STEP_SEP = "|||"

private fun stitchIndicatorFor(count: Int, pattern: String): String? {
    if (pattern.isBlank()) return null
    val safeCount = if (count < 0) 0 else count
    if (pattern.contains(STEP_SEP)) {
        val parts = pattern.split(STEP_SEP)
        val steps = parts.take(4).filter { it.isNotBlank() }
        if (steps.isEmpty()) return null
        val every = parts.getOrNull(4)?.toIntOrNull()?.coerceIn(1, 999) ?: 1
        return steps[(safeCount / every) % steps.size]
    }
    // Legacy K/P single-character format
    val cleaned = pattern.filter { it == 'K' || it == 'P' }
    if (cleaned.isEmpty()) return null
    return cleaned[safeCount % cleaned.length].toString()
}
