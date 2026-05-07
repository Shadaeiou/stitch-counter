package com.shadaeiou.stitchcounter.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun accentColorFor(bgArgb: Long): Color = when (bgArgb) {
    0xFF0A0A0AL -> Color(0xFFFBBF24)  // Black → amber/gold
    0xFF1B2A3AL -> Color(0xFF60A5FA)  // Slate → sky blue
    0xFF1E2A78L -> Color(0xFFFBBF24)  // Indigo → gold
    0xFF0E4F4FL -> Color(0xFFFB7185)  // Teal → rose
    0xFF134E2BL -> Color(0xFFE879F9)  // Forest → violet
    0xFF3F1A5CL -> Color(0xFF4ADE80)  // Plum → lime green
    0xFF5C1530L -> Color(0xFF67E8F9)  // Wine → cyan
    else -> Color(0xFFFBBF24)
}

@Composable
fun BottomToolbar(
    locked: Boolean,
    hasPdf: Boolean,
    pdfHidden: Boolean,
    hasPattern: Boolean,
    patternHidden: Boolean,
    counterBackgroundArgb: Long,
    onUploadPdf: () -> Unit,
    onTogglePdfHidden: () -> Unit,
    onTogglePatternHidden: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenPattern: () -> Unit,
    onOpenProjects: () -> Unit,
    onToggleLock: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showUploadMenu by remember { mutableStateOf(false) }
    var showPaneMenu by remember { mutableStateOf(false) }
    val accentColor = accentColorFor(counterBackgroundArgb)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left group: pane toggle, upload, notes
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Slot 1 — pane visibility toggle(s).
            when {
                hasPdf && hasPattern -> Box {
                    IconButton(onClick = { showPaneMenu = true }) {
                        Icon(
                            if (!pdfHidden || !patternHidden) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = "Toggle pane",
                            tint = if (!pdfHidden || !patternHidden) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DropdownMenu(expanded = showPaneMenu, onDismissRequest = { showPaneMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (pdfHidden) "Show PDF" else "Hide PDF") },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                            onClick = { showPaneMenu = false; onTogglePdfHidden() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (patternHidden) "Show Pattern" else "Hide Pattern") },
                            leadingIcon = { Icon(Icons.Default.Article, contentDescription = null) },
                            onClick = { showPaneMenu = false; onTogglePatternHidden() },
                        )
                    }
                }
                hasPdf -> IconButton(onClick = onTogglePdfHidden) {
                    Icon(
                        if (pdfHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (pdfHidden) "Show PDF" else "Hide PDF",
                        tint = if (pdfHidden) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface,
                    )
                }
                hasPattern -> IconButton(onClick = onTogglePatternHidden) {
                    Icon(
                        if (patternHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (patternHidden) "Show Pattern" else "Hide Pattern",
                        tint = if (patternHidden) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface,
                    )
                }
                else -> Spacer(Modifier.size(48.dp))
            }

            // Slot 2 — Upload / Import dropdown.
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
                        onClick = { showUploadMenu = false; onUploadPdf() },
                    )
                    DropdownMenuItem(
                        text = { Text("Import from web") },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                        onClick = { showUploadMenu = false; onOpenPattern() },
                    )
                }
            }

            // Slot 3 — Notes.
            IconButton(onClick = onOpenNotes) {
                Icon(Icons.Default.Notes, contentDescription = "Notes")
            }
        }

        // CENTER: Projects (accent color, larger icon, true center of toolbar).
        IconButton(onClick = onOpenProjects) {
            Icon(
                Icons.Default.Bookmarks,
                contentDescription = "My projects",
                tint = accentColor,
                modifier = Modifier.size(30.dp),
            )
        }

        // Right group: lock, settings
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Slot 5 — Lock.
            IconButton(onClick = onToggleLock) {
                Icon(
                    if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (locked) "Unlock" else "Lock",
                    tint = if (locked) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface,
                )
            }

            // Slot 6 — Settings.
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    }
}

@Composable
fun ProjectsToolbar(
    count: Int,
    currentRowLabel: String?,
    counterBackgroundArgb: Long,
    onGoToCounter: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = accentColorFor(counterBackgroundArgb)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left spacer balances the settings button on the right.
        Spacer(Modifier.weight(1f))

        // CENTER: Go to counter with live current-row icon.
        IconButton(onClick = onGoToCounter) {
            CurrentRowIcon(
                count = count,
                rowLabel = currentRowLabel,
                accentColor = accentColor,
            )
        }

        // Right: Settings (right-aligned in its half).
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    }
}

@Composable
private fun CurrentRowIcon(
    count: Int,
    rowLabel: String?,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (rowLabel != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(2.dp),
            ) {
                Text(
                    text = rowLabel.take(6),
                    color = accentColor,
                    fontSize = if (rowLabel.length <= 3) 13.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = (count + 1).toString(),
                    color = accentColor.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        } else {
            Text(
                text = (count + 1).toString(),
                color = accentColor,
                fontSize = when ((count + 1).toString().length) {
                    1 -> 18.sp
                    2 -> 15.sp
                    3 -> 12.sp
                    else -> 9.sp
                },
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
