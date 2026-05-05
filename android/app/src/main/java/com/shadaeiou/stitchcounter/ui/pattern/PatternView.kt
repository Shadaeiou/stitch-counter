package com.shadaeiou.stitchcounter.ui.pattern

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.shadaeiou.stitchcounter.ui.pdf.Stroke
import com.shadaeiou.stitchcounter.ui.pdf.StrokePoint
import com.shadaeiou.stitchcounter.viewmodel.Tool
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── Viewer HTML ───────────────────────────────────────────────────────────────

private val VIEWER_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { background: #1a1a1a; color: #f0f0f0; font-family: sans-serif; font-size: 16px; }
#content { padding: 12px 12px 120px; line-height: 1.7; word-break: break-word; }
ul, ol { padding-left: 24px; }
li { margin: 3px 0; }
#empty-msg { padding: 24px 16px; color: #666; font-size: 15px; }
.hl {
  outline: 3px solid #FF69B4;
  background: rgba(255, 105, 180, 0.12);
  border-radius: 4px;
  padding: 2px 4px;
}
.dm { opacity: 0.18; }
.drag-handle {
  display: none; position: fixed; left: 0; right: 0; height: 12px;
  background: rgba(255, 105, 180, 0.4);
  border-top: 2px solid #FF69B4;
  border-bottom: 2px solid #FF69B4;
  z-index: 200; touch-action: none; cursor: ns-resize;
}
.drag-handle::after {
  content: ''; display: block; width: 28px; height: 2px;
  background: rgba(255, 255, 255, 0.6); border-radius: 1px; margin: 4px auto;
}
/* Floating action bar — horizontal pill, bottom-centre */
#hl-action-bar {
  display: none; position: fixed; bottom: 88px;
  left: 50%; transform: translateX(-50%);
  z-index: 300;
  background: rgba(18, 18, 18, 0.96);
  border-radius: 40px;
  padding: 6px 10px;
  flex-direction: row; align-items: center; gap: 4px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.75);
  border: 1px solid rgba(255,255,255,0.12);
  white-space: nowrap;
}
.hl-btn {
  background: none; border: none; cursor: pointer;
  padding: 10px 16px; line-height: 1;
  border-radius: 32px; display: inline-flex;
  align-items: center; justify-content: center;
  font-size: 26px;
}
.hl-btn:active { background: rgba(255,255,255,0.12); }
#check-btn { color: #4ADE80; font-weight: bold; font-size: 30px; }
#pin-btn   { font-size: 26px; }
</style>
</head>
<body>
<div id="top-handle" class="drag-handle"></div>
<div id="bot-handle" class="drag-handle"></div>
<div id="hl-action-bar">
  <button class="hl-btn" id="check-btn" title="Save highlight">&#10003;</button>
  <button class="hl-btn" id="pin-btn"   title="Pin to counter">&#128204;</button>
</div>
<div id="content">
  <p id="empty-msg" style="color:#666;">No pattern saved. Tap <strong>Edit</strong> to add a pattern.</p>
</div>
<script>
var blocks = [];
var selStart = -1, selEnd = -1;
var inSelectionMode = false;

// Split any block element that has <br> children into separate <p> elements
// so each visual sentence/line is independently selectable.
function splitBrBlocks() {
  var c = document.getElementById('content');
  var children = Array.from(c.children);
  children.forEach(function(el) {
    var tag = el.tagName.toLowerCase();
    if (tag === 'ul' || tag === 'ol' || tag === 'table') return;
    if (!el.querySelector('br')) return;
    var parts = el.innerHTML.split(/<br\s*\/?>/i);
    parts.forEach(function(part) {
      var tmp = document.createElement('div');
      tmp.innerHTML = part;
      if (!tmp.textContent.trim()) return;
      var newEl = document.createElement('p');
      newEl.innerHTML = part.trim();
      c.insertBefore(newEl, el);
    });
    c.removeChild(el);
  });
}

function indexBlocks() {
  splitBrBlocks();
  blocks = Array.from(document.getElementById('content').children);
  blocks.forEach(function(b, i) { b.setAttribute('data-idx', i); });
}

function applyHighlight(s, e) {
  selStart = s; selEnd = e;
  inSelectionMode = true;
  blocks.forEach(function(b, i) {
    var inRange = i >= s && i <= e;
    b.classList.toggle('hl', inRange);
    b.classList.toggle('dm', !inRange);
  });
  positionHandles();
  showActionBar();
}

function clearHighlight() {
  selStart = -1; selEnd = -1;
  inSelectionMode = false;
  blocks.forEach(function(b) { b.classList.remove('hl', 'dm'); });
  document.getElementById('top-handle').style.display = 'none';
  document.getElementById('bot-handle').style.display = 'none';
  document.getElementById('hl-action-bar').style.display = 'none';
}

function confirmHighlight() {
  inSelectionMode = false;
  document.getElementById('top-handle').style.display = 'none';
  document.getElementById('bot-handle').style.display = 'none';
  document.getElementById('hl-action-bar').style.display = 'none';
}

function getHighlightedText() {
  if (selStart < 0) return '';
  var parts = [];
  for (var i = selStart; i <= Math.min(selEnd, blocks.length - 1); i++) {
    var txt = (blocks[i].innerText || '').replace(/\s+/g, ' ').trim();
    if (txt) parts.push(txt);
  }
  return parts.join(' ');
}

function positionHandles() {
  if (selStart < 0 || !inSelectionMode || !blocks[selStart]) {
    document.getElementById('top-handle').style.display = 'none';
    document.getElementById('bot-handle').style.display = 'none';
    return;
  }
  var tRect = blocks[selStart].getBoundingClientRect();
  var bRect = blocks[Math.min(selEnd, blocks.length-1)].getBoundingClientRect();
  var th = document.getElementById('top-handle');
  var bh = document.getElementById('bot-handle');
  th.style.display = 'block';
  th.style.top = Math.max(0, tRect.top - 6) + 'px';
  bh.style.display = 'block';
  bh.style.top = Math.min(window.innerHeight - 12, bRect.bottom - 6) + 'px';
}

function showActionBar() {
  document.getElementById('hl-action-bar').style.display =
    (selStart >= 0 && inSelectionMode) ? 'flex' : 'none';
}

window.addEventListener('scroll', function() {
  positionHandles();
}, {passive: true});

document.getElementById('check-btn').addEventListener('click', function() {
  confirmHighlight();
});
document.getElementById('pin-btn').addEventListener('click', function() {
  var text = getHighlightedText();
  if (text && typeof Android !== 'undefined') Android.onPin(text);
});

function setContent(html, range) {
  var c = document.getElementById('content');
  c.innerHTML = html || '<p id="empty-msg" style="color:#666;">No pattern saved. Tap <strong>Edit</strong> to add a pattern.</p>';
  indexBlocks();
  clearHighlight();
  if (range && range.indexOf(',') >= 0) {
    var parts = range.split(',');
    var s = parseInt(parts[0]), e = parseInt(parts[1]);
    if (!isNaN(s) && !isNaN(e) && s >= 0 && e >= s && e < blocks.length) {
      selStart = s; selEnd = e;
      // Restore in confirmed mode: highlight visible, handles/bar hidden.
      blocks.forEach(function(b, i) {
        b.classList.toggle('hl', i >= s && i <= e);
        b.classList.toggle('dm', !(i >= s && i <= e));
      });
    }
  }
}

// Long-press (500 ms) to select the nearest block element.
var lpTimer = null, lpStartX = 0, lpStartY = 0;
document.getElementById('content').addEventListener('touchstart', function(e) {
  var t = e.touches[0];
  lpStartX = t.clientX; lpStartY = t.clientY;
  lpTimer = setTimeout(function() {
    var el = document.elementFromPoint(lpStartX, lpStartY);
    if (!el) return;
    while (el && el.parentElement && el.parentElement.id !== 'content') el = el.parentElement;
    if (el && el.parentElement && el.parentElement.id === 'content') {
      var idx = parseInt(el.getAttribute('data-idx'));
      if (!isNaN(idx)) {
        applyHighlight(idx, idx);
        if (typeof Android !== 'undefined') Android.onHighlightChanged(idx + ',' + idx);
      }
    }
  }, 500);
}, {passive: true});
document.getElementById('content').addEventListener('touchend',
  function() { clearTimeout(lpTimer); }, {passive: true});
document.getElementById('content').addEventListener('touchmove', function(e) {
  var t = e.touches[0];
  if (Math.abs(t.clientX - lpStartX) > 8 || Math.abs(t.clientY - lpStartY) > 8) clearTimeout(lpTimer);
}, {passive: true});

// Drag handles to resize the selection.
function attachHandle(id, isTop) {
  var el = document.getElementById(id);
  el.addEventListener('touchstart', function(e) { e.stopPropagation(); }, {passive: false});
  el.addEventListener('touchmove', function(e) {
    e.preventDefault(); e.stopPropagation();
    if (!inSelectionMode) return;
    var cy = e.touches[0].clientY;
    var bestIdx = -1, bestDist = Infinity;
    blocks.forEach(function(b, i) {
      var r = b.getBoundingClientRect();
      var mid = (r.top + r.bottom) / 2;
      var d = Math.abs(mid - cy);
      if (d < bestDist) { bestDist = d; bestIdx = i; }
    });
    if (bestIdx < 0) return;
    var ns = isTop ? Math.min(bestIdx, selEnd < 0 ? bestIdx : selEnd)
                   : Math.min(selStart < 0 ? bestIdx : selStart, bestIdx);
    var ne = isTop ? Math.max(bestIdx, selEnd < 0 ? bestIdx : selEnd)
                   : Math.max(selStart < 0 ? bestIdx : selStart, bestIdx);
    if (ns !== selStart || ne !== selEnd) {
      applyHighlight(ns, ne);
      if (typeof Android !== 'undefined') Android.onHighlightChanged(ns + ',' + ne);
    }
  }, {passive: false});
}
attachHandle('top-handle', true);
attachHandle('bot-handle', false);
</script>
</body>
</html>
""".trimIndent()

// ── JS bridge ─────────────────────────────────────────────────────────────────

private class ViewerBridge(
    private val onHighlight: (String) -> Unit,
    private val onPin: (String) -> Unit,
) {
    @JavascriptInterface
    fun onHighlightChanged(range: String) {
        Handler(Looper.getMainLooper()).post { onHighlight(range) }
    }

    @JavascriptInterface
    fun onPin(text: String) {
        Handler(Looper.getMainLooper()).post { onPin(text) }
    }
}

// ── PatternView composable ─────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PatternView(
    patternHtml: String,
    highlightRange: String,
    strokes: List<Stroke>,
    patternTool: Tool,
    penColorArgb: Long,
    penWidthPx: Float,
    canRedo: Boolean,
    onHighlightChange: (String) -> Unit,
    onPinContent: (String) -> Unit,
    onAddStroke: (List<StrokePoint>) -> Unit,
    onEraseAt: (Float, Float) -> Unit,
    onUndoStroke: () -> Unit,
    onRedoStroke: () -> Unit,
    onSelectPen: () -> Unit,
    onSelectEraser: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val scrollY = remember { mutableIntStateOf(0) }
    var webViewReady by remember { mutableStateOf(false) }
    val bridge = remember { ViewerBridge(onHighlightChange, onPinContent) }

    // Load or refresh content when html / range changes.
    LaunchedEffect(webViewReady, patternHtml, highlightRange) {
        if (!webViewReady) return@LaunchedEffect
        val jsonHtml = Json.encodeToString(patternHtml)
        val jsonRange = Json.encodeToString(highlightRange)
        webViewRef.value?.evaluateJavascript("setContent($jsonHtml, $jsonRange)", null)
    }

    Box(modifier = modifier) {
        // WebView layer
        AndroidView(
            factory = { ctx ->
                WebView(ctx).also { wv ->
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.addJavascriptInterface(bridge, "Android")
                    wv.setOnScrollChangeListener { _, _, sy, _, _ -> scrollY.intValue = sy }
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            webViewReady = true
                        }
                    }
                    wv.loadDataWithBaseURL(
                        "https://stitchcounter.placeholder/",
                        VIEWER_HTML, "text/html", "UTF-8", null,
                    )
                    webViewRef.value = wv
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Stroke canvas overlay — document-space normalized coordinates.
        PatternStrokeCanvas(
            strokes = strokes,
            patternTool = patternTool,
            scrollYPx = scrollY.intValue,
            webView = webViewRef.value,
            penColorArgb = penColorArgb,
            penWidthPx = penWidthPx,
            onAddStroke = onAddStroke,
            onEraseAt = onEraseAt,
            modifier = Modifier.fillMaxSize(),
        )

        // Compact toolbar — top-right overlay
        PatternViewToolbar(
            patternTool = patternTool,
            hasStrokes = strokes.isNotEmpty(),
            canRedo = canRedo,
            hasHighlight = highlightRange.isNotEmpty(),
            onSelectPen = onSelectPen,
            onSelectEraser = onSelectEraser,
            onUndo = onUndoStroke,
            onRedo = onRedoStroke,
            onClearHighlight = {
                webViewRef.value?.evaluateJavascript("clearHighlight()", null)
                onHighlightChange("")
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        )

        // Edit FAB
        FloatingActionButton(
            onClick = onEdit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(Icons.Default.Edit, contentDescription = "Edit pattern")
        }
    }
}

// ── Stroke canvas ─────────────────────────────────────────────────────────────

@Composable
private fun PatternStrokeCanvas(
    strokes: List<Stroke>,
    patternTool: Tool,
    scrollYPx: Int,
    webView: WebView?,
    penColorArgb: Long,
    penWidthPx: Float,
    onAddStroke: (List<StrokePoint>) -> Unit,
    onEraseAt: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Captured in composition scope — safe to use inside pointerInput lambdas.
    val density = LocalDensity.current.density
    val currentPoints = remember { mutableListOf<StrokePoint>() }
    var drawTick by remember { mutableIntStateOf(0) }

    // Only intercept touch when a tool is active so the WebView can scroll
    // and handle long-press normally when in Tool.None mode.
    val touchModifier = if (patternTool != Tool.None) {
        Modifier.pointerInput(patternTool, density) {
            // Clear any in-progress stroke from the previous tool activation.
            currentPoints.clear(); drawTick++
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    // webView.contentHeight is in CSS px; density converts to physical px.
                    val contentHPx = ((webView?.contentHeight ?: 0) * density).coerceAtLeast(1f)
                    val scrollYf = scrollYPx.toFloat()
                    val viewW = size.width.toFloat().coerceAtLeast(1f)

                    fun norm(pos: Offset) = StrokePoint(
                        x = pos.x / viewW,
                        y = (pos.y + scrollYf) / contentHPx,
                    )

                    when (event.type) {
                        PointerEventType.Press -> {
                            change.consume()
                            currentPoints.clear()
                            if (patternTool == Tool.Eraser) {
                                val n = norm(change.position); onEraseAt(n.x, n.y)
                            } else {
                                currentPoints.add(norm(change.position))
                            }
                            drawTick++
                        }
                        PointerEventType.Move -> {
                            change.consume()
                            if (patternTool == Tool.Eraser) {
                                val n = norm(change.position); onEraseAt(n.x, n.y)
                            } else {
                                currentPoints.add(norm(change.position))
                            }
                            drawTick++
                        }
                        PointerEventType.Release -> {
                            if (patternTool == Tool.Pen && currentPoints.size >= 2) {
                                onAddStroke(currentPoints.toList())
                            }
                            currentPoints.clear(); drawTick++
                        }
                        else -> {}
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.then(touchModifier)) {
        @Suppress("UNUSED_EXPRESSION") drawTick

        val contentHPx = ((webView?.contentHeight ?: 0) * density).coerceAtLeast(1f)
        val scrollYf = scrollYPx.toFloat()
        val viewW = size.width
        val viewH = size.height

        fun toScreen(pt: StrokePoint) = Offset(
            x = pt.x * viewW,
            y = pt.y * contentHPx - scrollYf,
        )

        fun drawStrokes(sList: List<Stroke>) {
            for (stroke in sList) {
                if (stroke.points.size < 2) continue
                val path = Path()
                val pts = stroke.points.map { toScreen(it) }
                // Skip if entirely out of view
                if (pts.all { it.y < -stroke.widthPx || it.y > viewH + stroke.widthPx }) continue
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                drawPath(
                    path = path,
                    color = Color(stroke.colorArgb),
                    style = DrawStroke(
                        width = stroke.widthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        drawStrokes(strokes)

        // Draw in-progress stroke
        if (patternTool == Tool.Pen && currentPoints.size >= 2) {
            val path = Path()
            val pts = currentPoints.map { toScreen(it) }
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
            drawPath(
                path = path,
                color = Color(penColorArgb),
                style = DrawStroke(width = penWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

// ── Pattern view toolbar ──────────────────────────────────────────────────────

@Composable
private fun PatternViewToolbar(
    patternTool: Tool,
    hasStrokes: Boolean,
    canRedo: Boolean,
    hasHighlight: Boolean,
    onSelectPen: () -> Unit,
    onSelectEraser: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearHighlight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pen
        IconButton(onClick = onSelectPen, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Create,
                contentDescription = "Pen",
                tint = if (patternTool == Tool.Pen) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface,
            )
        }
        // Eraser
        IconButton(onClick = onSelectEraser, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Eraser",
                tint = if (patternTool == Tool.Eraser) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface,
            )
        }
        // Undo
        IconButton(onClick = onUndo, enabled = hasStrokes, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo stroke")
        }
        // Redo
        IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo stroke")
        }
        // Clear highlight
        if (hasHighlight) {
            IconButton(onClick = onClearHighlight, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear highlight",
                    tint = Color(0xFFFF69B4),
                )
            }
        }
    }
}
