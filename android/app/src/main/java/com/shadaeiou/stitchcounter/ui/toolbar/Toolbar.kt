package com.shadaeiou.stitchcounter.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomToolbar(
    locked: Boolean,
    hasPdf: Boolean,
    pdfHidden: Boolean,
    onUploadPdf: () -> Unit,
    onTogglePdfHidden: () -> Unit,
    onOpenNotes: () -> Unit,
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
        // Upload only lives here when no PDF has been loaded yet (entry
        // point). Once a PDF is loaded it lives in the in-pane PdfToolbar.
        if (!hasPdf) {
            IconButton(onClick = onUploadPdf) {
                Icon(Icons.Default.UploadFile, contentDescription = "Upload PDF")
            }
        }
        if (hasPdf) {
            IconButton(onClick = onTogglePdfHidden) {
                Icon(
                    if (pdfHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (pdfHidden) "Show PDF" else "Hide PDF",
                    tint = if (pdfHidden)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        IconButton(onClick = onOpenNotes) {
            Icon(Icons.Default.Notes, contentDescription = "Notes")
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
