package com.shadaeiou.stitchcounter.ui.counter

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CounterArea(
    count: Int,
    label: String,
    locked: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onLabelChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var labelEditing by remember { mutableStateOf(false) }
    var labelDraft by remember(label) { mutableStateOf(label) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .alpha(if (locked) 0.4f else 1f)
            .pointerInput(locked) {
                if (locked) return@pointerInput
                detectTapGestures(
                    onTap = { onIncrement() },
                    onLongPress = { onDecrement() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp),
        ) {
            if (labelEditing) {
                TextField(
                    value = labelDraft,
                    onValueChange = { labelDraft = it },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
            } else {
                Text(
                    text = label.ifBlank { "Tap to set label" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (label.isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                labelEditing = true
                            })
                        },
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (labelEditing) {
        // commit on focus loss / back — for MVP, commit immediately if user types and tap outside.
        // Simplest: commit when label area is no longer being edited via a side effect.
        androidx.compose.runtime.DisposableEffect(Unit) {
            onDispose {
                onLabelChange(labelDraft)
            }
        }
    }
}
