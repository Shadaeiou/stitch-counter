package com.shadaeiou.stitchcounter.ui.projects

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadaeiou.stitchcounter.data.db.entities.KnitProject
import com.shadaeiou.stitchcounter.viewmodel.KnitProjectViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val STATUS_QUEUE = "queue"
private const val STATUS_PROGRESS = "progress"
private const val STATUS_FINISHED = "finished"
private const val CRAFT_KNIT = "knit"
private const val CRAFT_CROCHET = "crochet"

@Composable
fun ProjectsScreen(vm: KnitProjectViewModel, onBack: () -> Unit) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    var detailId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    if (detailId == null) {
        BackHandler { onBack() }
        ProjectListView(
            projects = projects,
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
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = statusFilter == STATUS_QUEUE,
                    onClick = { statusFilter = STATUS_QUEUE },
                    label = { Text("In Queue") },
                )
                FilterChip(
                    selected = statusFilter == STATUS_PROGRESS,
                    onClick = { statusFilter = STATUS_PROGRESS },
                    label = { Text("In Progress") },
                )
                FilterChip(
                    selected = statusFilter == STATUS_FINISHED,
                    onClick = { statusFilter = STATUS_FINISHED },
                    label = { Text("Finished") },
                )
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
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
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(4.dp),
    ) {
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
    vm: KnitProjectViewModel,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var craftType by remember { mutableStateOf(CRAFT_KNIT) }
    var status by remember { mutableStateOf(STATUS_QUEUE) }
    var yarnBought by remember { mutableStateOf(false) }
    var needleSize by remember { mutableStateOf("") }
    var patternSource by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var heroBitmap by remember(photoPath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(project) {
        if (project != null && !initialized) {
            title = project.title
            craftType = project.craftType
            status = project.status
            yarnBought = project.yarnBought
            needleSize = project.needleSize
            patternSource = project.patternSource
            notes = project.notes
            photoPath = project.photoPath
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
                yarnBought = yarnBought,
                needleSize = needleSize,
                patternSource = patternSource,
                notes = notes,
                photoPath = photoPath,
            )
        )
        onBack()
    }

    BackHandler { saveAndBack() }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyPhotoToInternal(context, uri) }
            if (path != null) photoPath = path
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
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete project",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
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

            // Form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Project title") },
                    singleLine = true,
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Craft type",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = craftType == CRAFT_KNIT,
                            onClick = { craftType = CRAFT_KNIT },
                            label = { Text("Knit") },
                        )
                        FilterChip(
                            selected = craftType == CRAFT_CROCHET,
                            onClick = { craftType = CRAFT_CROCHET },
                            label = { Text("Crochet") },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = status == STATUS_QUEUE,
                            onClick = { status = STATUS_QUEUE },
                            label = { Text("In Queue") },
                        )
                        FilterChip(
                            selected = status == STATUS_PROGRESS,
                            onClick = { status = STATUS_PROGRESS },
                            label = { Text("In Progress") },
                        )
                        FilterChip(
                            selected = status == STATUS_FINISHED,
                            onClick = { status = STATUS_FINISHED },
                            label = { Text("Finished") },
                        )
                    }
                }

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

                Spacer(Modifier.height(72.dp))
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private fun copyPhotoToInternal(context: Context, uri: Uri): String? {
    val dir = File(context.filesDir, "project_photos").apply { mkdirs() }
    val dest = File(dir, "${System.currentTimeMillis()}.jpg")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
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
        while ((opts.outWidth / sample) > maxPx || (opts.outHeight / sample) > maxPx) {
            sample *= 2
        }
        opts.inSampleSize = sample
        opts.inJustDecodeBounds = false
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
