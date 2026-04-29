package com.shadaeiou.stitchcounter.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shadaeiou.stitchcounter.ui.counter.CounterArea
import com.shadaeiou.stitchcounter.ui.pdf.PdfViewer
import com.shadaeiou.stitchcounter.ui.pdf.copyPdfToInternal
import com.shadaeiou.stitchcounter.ui.toolbar.BottomToolbar
import com.shadaeiou.stitchcounter.viewmodel.CounterViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
) {
    val vm: CounterViewModel = viewModel(factory = CounterViewModel.Factory())
    val project by vm.project.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var inverted by remember { mutableStateOf(false) }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = copyPdfToInternal(context, uri)
            if (path != null) vm.setPdfPath(path)
            else scope.launch { snackbar.showSnackbar("Could not import PDF") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            CounterArea(
                count = project?.count ?: 0,
                label = project?.label.orEmpty(),
                locked = locked,
                onIncrement = vm::increment,
                onDecrement = vm::decrement,
                onLabelChange = vm::setLabel,
                modifier = Modifier.weight(1f),
            )
            PdfViewer(
                pdfPath = project?.pdfPath,
                page = project?.currentPage ?: 0,
                invertColors = inverted,
                onPageChange = vm::setPage,
                modifier = Modifier.weight(1f),
            )
            BottomToolbar(
                locked = locked,
                inverted = inverted,
                onUploadPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                onPenStub = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() },
                onEraserStub = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() },
                onNotesStub = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() },
                onToggleInvert = { inverted = !inverted },
                onToggleLock = vm::toggleLock,
                onOpenSettings = onOpenSettings,
            )
        }
    }

    LaunchedEffect(Unit) {
        // placeholder for any one-shot startup work
    }
}
