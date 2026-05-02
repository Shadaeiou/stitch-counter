package com.shadaeiou.stitchcounter.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onOpenPattern: () -> Unit,
    onToggleLock: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showUploadMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1 — Show/Hide PDF when a PDF is loaded; placeholder otherwise so
        // the remaining buttons stay centred.
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
        } else {
            Spacer(Modifier.size(48.dp))
        }

        // Slot 2 — Upload / Import dropdown (second from left, always visible).
        Box {
            IconButton(onClick = { showUploadMenu = true }) {
                Icon(Icons.Default.UploadFile, contentDescription = "Upload or import")
            }
            DropdownMenu(
                expanded = showUploadMenu,
                onDismissRequest = { showUploadMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Upload PDF") },
                    leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    onClick = {
                        showUploadMenu = false
                        onUploadPdf()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Import from web") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                    onClick = {
                        showUploadMenu = false
                        onOpenPattern()
                    },
                )
            }
        }

        // Slot 3 — Notes (centre).
        IconButton(onClick = onOpenNotes) {
            Icon(Icons.Default.Notes, contentDescription = "Notes")
        }

        // Slot 4 — Lock.
        IconButton(onClick = onToggleLock) {
            Icon(
                if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (locked) "Unlock" else "Lock",
                tint = if (locked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }

        // Slot 5 — Settings.
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}
