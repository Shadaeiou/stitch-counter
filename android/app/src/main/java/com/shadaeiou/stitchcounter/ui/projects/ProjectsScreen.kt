package com.shadaeiou.stitchcounter.ui.projects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadaeiou.stitchcounter.data.db.entities.KnitProject
import com.shadaeiou.stitchcounter.ui.toolbar.ProjectsToolbar
import com.shadaeiou.stitchcounter.viewmodel.KnitProjectViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt

private const val STATUS_QUEUE = "queue"
private const val STATUS_PROGRESS = "progress"
private const val STATUS_FINISHED = "finished"
private const val CRAFT_KNIT = "knit"
private const val CRAFT_CROCHET = "crochet"

private enum class CropHandle { None, TopLeft, TopRight, BottomLeft, BottomRight, Interior }

private fun cropDist(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx; val dy = ay - by
    return sqrt(dx * dx + dy * dy)
}

private fun toTitleCase(s: String): String =
    s.split(" ").joinToString(" ") { word ->
        if (word.isNotEmpty()) word[0].uppercaseChar() + word.substring(1)
        else word
    }

@Composable
fun ProjectsScreen(
    vm: KnitProjectViewModel,
    count: Int,
    currentRowLabel: String?,
    counterBackgroundArgb: Long,
    onGoToCounter: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    var detailId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    if (detailId == null) {
        BackHandler { onBack() }
        ProjectListView(
            projects = projects,
            count = count,
            currentRowLabel = currentRowLabel,
            counterBackgroundArgb = counterBackgroundArgb,
            onGoToCounter = onGoToCounter,
            onOpenSettings = onOpenSettings,
            onBack = onBack,
            onOpenDetail = { detailId = it },
            onCreate = {
                scope.launch {
                    val id = vm.create()
                    detailId = id
                }
            },
        )
    } else {
        val id = detailId!!
        val project = projects.find { it.id == id }
        ProjectDetailView(
            project = project,
            count = count,
            currentRowLabel = currentRowLabel,
            counterBackgroundArgb = counterBackgroundArgb,
            onGoToCounter = onGoToCounter,
            onOpenSettings = onOpenSettings,
            vm = vm,
            onBack = { detailId = null },
            onDelete = {
                vm.delete(id)
                detailId = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectListView(
    projects: List<KnitProject>,
    count: Int,
    currentRowLabel: String?,
    counterBackgroundArgb: Long,
    onGoToCounter: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onCreate: () -> Unit,
) {
    var statusFilter by remember { mutableStateOf<String?>(null) }
    val filtered = if (statusFilter == null) projects
                   else projects.filter { it.status == statusFilter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Projects") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            ProjectsToolbar(
                count = count,
                currentRowLabel = currentRowLabel,
                counterBackgroundArgb = counterBackgroundArgb,
                onGoToCounter = onGoToCounter,
                onOpenSettings = onOpenSettings,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "New project")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("All") })
                FilterChip(selected = statusFilter == STATUS_QUEUE, onClick = { statusFilter = STATUS_QUEUE }, label = { Text("In Queue") })
                FilterChip(selected = statusFilter == STATUS_PROGRESS, onClick = { statusFilter = STATUS_PROGRESS }, label = { Text("In Progress") })
                FilterChip(selected = statusFilter == STATUS_FINISHED, onClick = { statusFilter = STATUS_FINISHED }, label = { Text("Finished") })
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        )
                        Text(
                            if (statusFilter == null) "No projects yet"
                            else "No projects with this status",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        if (statusFilter == null) {
                            Text(
                                "Tap + to add your first project",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id }) { project ->
                        ProjectCard(project = project, onClick = { onOpenDetail(project.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: KnitProject, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectThumbnail(
                photoPath = project.photoPath,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = project.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CraftTypeBadge(project.craftType)
                    StatusBadge(project.status)
                }
                if (project.yarnBought) {
                    Text(
                        "✓ Yarn bought",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectThumbnail(photoPath: String?, modifier: Modifier) {
    var bitmap by remember(photoPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoPath) {
        bitmap = withContext(Dispatchers.IO) {
            photoPath?.let { loadBitmap(it, maxPx = 256) }
        }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun CraftTypeBadge(craftType: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = if (craftType == CRAFT_CROCHET) "Crochet" else "Knit",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    val label: String
    val bgColor: Color
    val textColor: Color
    when (status) {
        STATUS_PROGRESS -> {
            label = "In Progress"
            bgColor = Color(0xFFFF8C00).copy(alpha = 0.15f)
            textColor = Color(0xFFFF8C00)
        }
        STATUS_FINISHED -> {
            label = "Finished"
            bgColor = Color(0xFF2E7D32).copy(alpha = 0.15f)
            textColor = Color(0xFF2E7D32)
        }
        else -> {
            label = "In Queue"
            bgColor = MaterialTheme.colorScheme.primaryContainer
            textColor = MaterialTheme.colorScheme.primary
        }
    }
    Surface(color = bgColor, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDetailView(
    project: KnitProject?,
    count: Int,
    currentRowLabel: String?,
    counterBackgroundArgb: Long,
    onGoToCounter: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: KnitProjectViewModel,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var craftType by remember { mutableStateOf(CRAFT_KNIT) }
    var status by remember { mutableStateOf(STATUS_QUEUE) }
    var patternSecured by remember { mutableStateOf(false) }
    var yarnBought by remember { mutableStateOf(false) }
    var yarnWeight by remember { mutableStateOf("") }
    var needleSize by remember { mutableStateOf("") }
    var patternSource by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var projectPdfPath by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var heroBitmap by remember(photoPath) { mutableStateOf<ImageBitmap?>(null) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }
    var showProjectPdf by remember { mutableStateOf(false) }

    LaunchedEffect(project) {
        if (project != null && !initialized) {
            title = project.title
            craftType = project.craftType
            status = project.status
            patternSecured = project.patternSecured
            yarnBought = project.yarnBought
            yarnWeight = project.yarnWeight
            needleSize = project.needleSize
            patternSource = project.patternSource
            notes = project.notes
            photoPath = project.photoPath
            projectPdfPath = project.projectPdfPath
            initialized = true
        }
    }

    LaunchedEffect(photoPath) {
        heroBitmap = withContext(Dispatchers.IO) {
            photoPath?.let { loadBitmap(it, maxPx = 1024) }
        }
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun saveAndBack() {
        val p = project ?: run { onBack(); return }
        vm.update(
            p.copy(
                title = title,
                craftType = craftType,
                status = status,
                patternSecured = patternSecured,
                yarnBought = yarnBought,
                yarnWeight = yarnWeight,
                needleSize = needleSize,
                patternSource = patternSource,
                notes = notes,
                photoPath = photoPath,
                projectPdfPath = projectPdfPath,
            )
        )
        onBack()
    }

    // Full-screen PDF viewer — shown above everything else.
    if (showProjectPdf && projectPdfPath != null) {
        BackHandler { showProjectPdf = false }
        ProjectPdfViewer(
            pdfPath = projectPdfPath!!,
            onClose = { showProjectPdf = false },
        )
        return
    }

    // Crop screen — shown after the user picks a photo.
    if (cropSourceUri != null) {
        BackHandler { cropSourceUri = null }
        CropScreen(
            sourceUri = cropSourceUri!!,
            context = context,
            onConfirm = { path ->
                photoPath = path
                cropSourceUri = null
            },
            onCancel = { cropSourceUri = null },
        )
        return
    }

    BackHandler { saveAndBack() }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        cropSourceUri = uri
    }

    val projectPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyProjectPdfToInternal(context, uri) }
            if (path != null) projectPdfPath = path
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { "New Project" }) },
                navigationIcon = {
                    IconButton(onClick = { saveAndBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and go back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete project", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        bottomBar = {
            ProjectsToolbar(
                count = count,
                currentRowLabel = currentRowLabel,
                counterBackgroundArgb = counterBackgroundArgb,
                onGoToCounter = onGoToCounter,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Photo hero
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (photoPath == null) Modifier.clickable { photoPicker.launch("image/*") }
                        else Modifier
                    ),
            ) {
                val bmp = heroBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Project photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap to add a photo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
                SmallFloatingActionButton(
                    onClick = { photoPicker.launch("image/*") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Icon(
                        if (photoPath != null) Icons.Default.Edit else Icons.Default.CameraAlt,
                        contentDescription = if (photoPath != null) "Change photo" else "Add photo",
                    )
                }
            }

            // Form fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = toTitleCase(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Project title") },
                    singleLine = true,
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Craft type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = craftType == CRAFT_KNIT, onClick = { craftType = CRAFT_KNIT }, label = { Text("Knit") })
                        FilterChip(selected = craftType == CRAFT_CROCHET, onClick = { craftType = CRAFT_CROCHET }, label = { Text("Crochet") })
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = status == STATUS_QUEUE, onClick = { status = STATUS_QUEUE }, label = { Text("In Queue") })
                        FilterChip(selected = status == STATUS_PROGRESS, onClick = { status = STATUS_PROGRESS }, label = { Text("In Progress") })
                        FilterChip(selected = status == STATUS_FINISHED, onClick = { status = STATUS_FINISHED }, label = { Text("Finished") })
                    }
                }

                // Pattern secured checkbox (above Yarn bought)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { patternSecured = !patternSecured },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = patternSecured, onCheckedChange = { patternSecured = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Pattern secured", style = MaterialTheme.typography.bodyLarge)
                }

                // Yarn bought checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { yarnBought = !yarnBought },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = yarnBought, onCheckedChange = { yarnBought = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Yarn bought", style = MaterialTheme.typography.bodyLarge)
                }

                // Yarn weight (above needle size)
                OutlinedTextField(
                    value = yarnWeight,
                    onValueChange = { yarnWeight = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Yarn weight") },
                    placeholder = { Text("e.g. DK, Worsted, Bulky") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = needleSize,
                    onValueChange = { needleSize = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (craftType == CRAFT_KNIT) "Needle size" else "Hook size") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = patternSource,
                    onValueChange = { patternSource = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pattern source") },
                    placeholder = { Text("URL, book, or designer") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes") },
                    minLines = 4,
                )

                // Project PDF section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pattern PDF",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    if (projectPdfPath == null) {
                        OutlinedButton(
                            onClick = { projectPdfPicker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Upload PDF")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { showProjectPdf = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("View PDF")
                            }
                            IconButton(onClick = { projectPdfPath = null }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove PDF", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Save button
                Button(
                    onClick = { saveAndBack() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save")
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete project?") },
            text = { Text("\"${title.ifBlank { "Untitled" }}\" will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Read-only PDF viewer ──────────────────────────────────────────────────────

private class ProjectPdfHandle(pdfPath: String) : AutoCloseable {
    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(pdfPath), ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer: PdfRenderer = PdfRenderer(pfd)
    val pageCount: Int get() = renderer.pageCount

    fun renderPage(index: Int): Bitmap? = runCatching {
        renderer.openPage(index).use { page ->
            val width = 1600
            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }.getOrNull()

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

@Composable
private fun ProjectPdfViewer(pdfPath: String, onClose: () -> Unit) {
    val handle = remember(pdfPath) { runCatching { ProjectPdfHandle(pdfPath) }.getOrNull() }
    DisposableEffect(handle) { onDispose { handle?.close() } }

    if (handle == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Could not open PDF", color = Color.White)
                Button(onClick = onClose) { Text("Done") }
            }
        }
        return
    }

    var page by remember { mutableStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(page, pdfPath) {
        scale = 1f; offsetX = 0f; offsetY = 0f
        bitmap = withContext(Dispatchers.IO) { handle.renderPage(page) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 8f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF page ${page + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY,
                        transformOrigin = TransformOrigin.Center,
                    ),
                contentScale = ContentScale.Fit,
            )
        }

        // Top bar: close + page indicator
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Done", tint = Color.White)
            }
            Text(
                "Page ${page + 1} of ${handle.pageCount}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Page navigation
        if (handle.pageCount > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { page-- }, enabled = page > 0) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { page++ }, enabled = page < handle.pageCount - 1) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next page", tint = Color.White)
                }
            }
        }
    }
}

// ── Photo crop screen ─────────────────────────────────────────────────────────

@Composable
private fun CropScreen(
    sourceUri: Uri,
    context: Context,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(sourceUri) {
        sourceBitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it)
            }
        }
    }

    val bmp = sourceBitmap
    if (bmp == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    var cropLeft by remember { mutableStateOf(0f) }
    var cropTop by remember { mutableStateOf(0f) }
    var cropRight by remember { mutableStateOf(1f) }
    var cropBottom by remember { mutableStateOf(1f) }
    var dragTarget by remember { mutableStateOf(CropHandle.None) }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
        ) {
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            val bmpAspect = bmp.width.toFloat() / bmp.height
            val boxAspect = boxW / boxH
            val imgW: Float
            val imgH: Float
            if (bmpAspect > boxAspect) {
                imgW = boxW; imgH = boxW / bmpAspect
            } else {
                imgH = boxH; imgW = boxH * bmpAspect
            }
            val imgX = (boxW - imgW) / 2f
            val imgY = (boxH - imgH) / 2f

            val handleTouchRadius = with(density) { 40.dp.toPx() }

            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imgX, imgY, imgW, imgH) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val cL = imgX + cropLeft * imgW
                                val cT = imgY + cropTop * imgH
                                val cR = imgX + cropRight * imgW
                                val cB = imgY + cropBottom * imgH
                                dragTarget = when {
                                    cropDist(offset.x, offset.y, cL, cT) < handleTouchRadius -> CropHandle.TopLeft
                                    cropDist(offset.x, offset.y, cR, cT) < handleTouchRadius -> CropHandle.TopRight
                                    cropDist(offset.x, offset.y, cL, cB) < handleTouchRadius -> CropHandle.BottomLeft
                                    cropDist(offset.x, offset.y, cR, cB) < handleTouchRadius -> CropHandle.BottomRight
                                    offset.x in cL..cR && offset.y in cT..cB -> CropHandle.Interior
                                    else -> CropHandle.None
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x / imgW
                                val dy = dragAmount.y / imgH
                                when (dragTarget) {
                                    CropHandle.TopLeft -> {
                                        cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.05f)
                                        cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.05f)
                                    }
                                    CropHandle.TopRight -> {
                                        cropRight = (cropRight + dx).coerceIn(cropLeft + 0.05f, 1f)
                                        cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.05f)
                                    }
                                    CropHandle.BottomLeft -> {
                                        cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.05f)
                                        cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.05f, 1f)
                                    }
                                    CropHandle.BottomRight -> {
                                        cropRight = (cropRight + dx).coerceIn(cropLeft + 0.05f, 1f)
                                        cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.05f, 1f)
                                    }
                                    CropHandle.Interior -> {
                                        val w = cropRight - cropLeft
                                        val h = cropBottom - cropTop
                                        cropLeft = (cropLeft + dx).coerceIn(0f, 1f - w)
                                        cropTop = (cropTop + dy).coerceIn(0f, 1f - h)
                                        cropRight = cropLeft + w
                                        cropBottom = cropTop + h
                                    }
                                    CropHandle.None -> {}
                                }
                            },
                            onDragEnd = { dragTarget = CropHandle.None },
                        )
                    },
            ) {
                val cL = imgX + cropLeft * imgW
                val cT = imgY + cropTop * imgH
                val cR = imgX + cropRight * imgW
                val cB = imgY + cropBottom * imgH

                val overlay = Color.Black.copy(alpha = 0.55f)
                drawRect(overlay, Offset(0f, 0f), Size(size.width, cT))
                drawRect(overlay, Offset(0f, cB), Size(size.width, size.height - cB))
                drawRect(overlay, Offset(0f, cT), Size(cL, cB - cT))
                drawRect(overlay, Offset(cR, cT), Size(size.width - cR, cB - cT))

                drawRect(
                    Color.White,
                    Offset(cL, cT),
                    Size(cR - cL, cB - cT),
                    style = Stroke(width = 2.dp.toPx()),
                )

                val hLen = 24.dp.toPx()
                val hW = 4f
                drawLine(Color.White, Offset(cL, cT), Offset(cL + hLen, cT), hW)
                drawLine(Color.White, Offset(cL, cT), Offset(cL, cT + hLen), hW)
                drawLine(Color.White, Offset(cR - hLen, cT), Offset(cR, cT), hW)
                drawLine(Color.White, Offset(cR, cT), Offset(cR, cT + hLen), hW)
                drawLine(Color.White, Offset(cL, cB - hLen), Offset(cL, cB), hW)
                drawLine(Color.White, Offset(cL, cB), Offset(cL + hLen, cB), hW)
                drawLine(Color.White, Offset(cR, cB - hLen), Offset(cR, cB), hW)
                drawLine(Color.White, Offset(cR - hLen, cB), Offset(cR, cB), hW)
            }
        }

        // Confirm / Cancel buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text("Cancel") }
            Button(onClick = {
                scope.launch {
                    val path = withContext(Dispatchers.IO) {
                        val x = (cropLeft * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                        val y = (cropTop * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                        val w = ((cropRight - cropLeft) * bmp.width).toInt().coerceIn(1, bmp.width - x)
                        val h = ((cropBottom - cropTop) * bmp.height).toInt().coerceIn(1, bmp.height - y)
                        val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
                        saveCroppedBitmap(context, cropped)
                    }
                    if (path != null) onConfirm(path) else onCancel()
                }
            }) { Text("Use Photo") }
        }
    }
}

// ── File helpers ──────────────────────────────────────────────────────────────

private fun copyProjectPdfToInternal(context: Context, uri: Uri): String? {
    val dir = File(context.filesDir, "project_pdfs").apply { mkdirs() }
    val dest = File(dir, "${System.currentTimeMillis()}.pdf")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    } catch (e: Exception) {
        null
    }
}

private fun saveCroppedBitmap(context: Context, bitmap: Bitmap): String? {
    val dir = File(context.filesDir, "project_photos").apply { mkdirs() }
    val dest = File(dir, "${System.currentTimeMillis()}.jpg")
    return try {
        dest.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        dest.absolutePath
    } catch (e: Exception) {
        null
    }
}

private fun loadBitmap(path: String, maxPx: Int): ImageBitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while ((opts.outWidth / sample) > maxPx || (opts.outHeight / sample) > maxPx) sample *= 2
        opts.inSampleSize = sample
        opts.inJustDecodeBounds = false
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
