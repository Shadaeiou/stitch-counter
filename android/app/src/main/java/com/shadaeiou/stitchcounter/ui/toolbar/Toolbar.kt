package com.shadaeiou.stitchcounter.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shadaeiou.stitchcounter.viewmodel.Tool

@Composable
fun BottomToolbar(
    locked: Boolean,
    inverted: Boolean,
    activeTool: Tool,
    onUploadPdf: () -> Unit,
    onSelectPen: () -> Unit,
    onSelectEraser: () -> Unit,
    onOpenNotes: () -> Unit,
    onToggleInvert: () -> Unit,
    onToggleLock: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onUploadPdf) {
            Icon(Icons.Default.UploadFile, contentDescription = "Upload PDF")
        }
        IconButton(onClick = onSelectPen) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Pen",
                tint = if (activeTool == Tool.Pen)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onSelectEraser) {
            Icon(
                Icons.Default.AutoFixHigh,
                contentDescription = "Eraser",
                tint = if (activeTool == Tool.Eraser)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onOpenNotes) {
            Icon(Icons.Default.Notes, contentDescription = "Notes")
        }
        IconButton(onClick = onToggleInvert) {
            Icon(
                Icons.Default.InvertColors,
                contentDescription = "Invert colors",
                tint = if (inverted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onToggleLock) {
            Icon(
                if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Lock",
                tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}
