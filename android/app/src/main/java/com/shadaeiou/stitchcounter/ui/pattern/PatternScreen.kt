package com.shadaeiou.stitchcounter.ui.pattern

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadaeiou.stitchcounter.viewmodel.CounterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Bright highlight colour options shown in the toolbar.
private val HIGHLIGHT_COLORS = listOf(
    Color(0xFFFFFF00) to "#FFFF00",   // yellow
    Color(0xFF90EE90) to "#90EE90",   // light green
    Color(0xFF00FFFF) to "#00FFFF",   // cyan
    Color(0xFFFF69B4) to "#FF69B4",   // hot pink
    Color(0xFFFFA500) to "#FFA500",   // orange
    Color(0xFFADD8E6) to "#ADD8E6",   // light blue
)

// HTML shell loaded into the WebView.
private val EDITOR_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background: #1a1a1a;
    color: #f0f0f0;
    font-family: sans-serif;
    font-size: 16px;
  }
  #editor {
    min-height: 100vh;
    padding: 12px;
    outline: none;
    word-break: break-word;
    line-height: 1.7;
    caret-color: #22c55e;
  }
  #editor:empty:before {
    content: "Pattern text will appear here after fetching a URL.  You can also type or paste directly.";
    color: #666;
    display: block;
  }
  ul, ol { padding-left: 24px; }
  li { margin: 3px 0; }
</style>
</head>
<body>
<div id="editor" contenteditable="true"></div>
<script>
function setContent(html) {
  document.getElementById('editor').innerHTML = html;
}
function getContent() {
  return document.getElementById('editor').innerHTML;
}
function execCmd(cmd, val) {
  document.execCommand(cmd, false, val != null ? val : null);
  document.getElementById('editor').focus();
}
</script>
</body>
</html>
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PatternScreen(
    vm: CounterViewModel,
    onBack: () -> Unit,
) {
    val savedPattern by vm.patternHtml.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewReady by remember { mutableStateOf(false) }

    val bridge = remember { EditorBridge() }

    // Inject saved pattern once the WebView has finished loading.
    LaunchedEffect(webViewReady, savedPattern) {
        if (webViewReady && savedPattern.isNotEmpty()) {
            val jsonEncoded = Json.encodeToString(savedPattern)
            webViewRef?.evaluateJavascript("setContent($jsonEncoded)", null)
        }
    }

    fun fetchUrl() {
        val url = urlInput.trim()
        if (url.isEmpty()) return
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PatternFetcher.fetchPatternFromUrl(url)
            }
            isLoading = false
            if (result.isSuccess) {
                val html = plainTextToHtml(result.getOrThrow())
                val jsonEncoded = Json.encodeToString(html)
                webViewRef?.evaluateJavascript("setContent($jsonEncoded)", null)
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to fetch URL"
            }
        }
    }

    fun saveContent() {
        bridge.onContent = { html ->
            scope.launch { vm.setPatternHtml(html) }
            onBack()
        }
        webViewRef?.evaluateJavascript(
            "Android.receiveContent(document.getElementById('editor').innerHTML)",
            null,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pattern") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (webViewReady) saveContent() },
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                text = { Text("Save") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── URL import row ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("Paste URL to import pattern…") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(
                    onClick = { fetchUrl() },
                    enabled = !isLoading && urlInput.isNotBlank(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Go", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(4.dp))
            }

            // ── Formatting toolbar ───────────────────────────────────────────
            PatternFormatToolbar(
                enabled = webViewReady,
                onCommand = { js -> webViewRef?.evaluateJavascript(js, null) },
            )

            // ── WebView editor ───────────────────────────────────────────────
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).also { wv ->
                        wv.settings.javaScriptEnabled = true
                        wv.settings.domStorageEnabled = true
                        wv.addJavascriptInterface(bridge, "Android")
                        wv.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                webViewReady = true
                            }
                        }
                        wv.loadDataWithBaseURL(null, EDITOR_HTML, "text/html", "UTF-8", null)
                        webViewRef = wv
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

// ── Formatting toolbar ────────────────────────────────────────────────────────

@Composable
private fun PatternFormatToolbar(
    enabled: Boolean,
    onCommand: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(
            onClick = { onCommand("execCmd('bold')") },
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Default.FormatBold, contentDescription = "Bold")
        }
        IconButton(
            onClick = { onCommand("execCmd('underline')") },
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline")
        }
        IconButton(
            onClick = { onCommand("execCmd('insertUnorderedList')") },
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullet list")
        }
        IconButton(
            onClick = { onCommand("execCmd('insertOrderedList')") },
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbered list")
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
        )

        FontSizeButton("A", 11.sp, "Small text", enabled) { onCommand("execCmd('fontSize','2')") }
        FontSizeButton("A", 15.sp, "Normal text", enabled) { onCommand("execCmd('fontSize','3')") }
        FontSizeButton("A", 19.sp, "Large text", enabled) { onCommand("execCmd('fontSize','5')") }
        FontSizeButton("A", 23.sp, "Extra-large text", enabled) { onCommand("execCmd('fontSize','7')") }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
        )

        for ((composeColor, hexColor) in HIGHLIGHT_COLORS) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(composeColor)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape)
                    .clickable(enabled = enabled) {
                        onCommand("execCmd('hiliteColor','$hexColor')")
                    },
            )
        }

        // Remove highlight
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape)
                .clickable(enabled = enabled) {
                    onCommand("execCmd('hiliteColor','inherit')")
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun FontSizeButton(
    label: String,
    textSize: androidx.compose.ui.unit.TextUnit,
    contentDesc: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            color = if (enabled)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

// ── JS ↔ Kotlin bridge ────────────────────────────────────────────────────────

private class EditorBridge {
    var onContent: ((String) -> Unit)? = null

    @JavascriptInterface
    fun receiveContent(html: String) {
        val callback = onContent ?: return
        onContent = null
        Handler(Looper.getMainLooper()).post { callback(html) }
    }
}
