package com.shadaeiou.stitchcounter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadaeiou.stitchcounter.StitchCounterApp
import com.shadaeiou.stitchcounter.data.db.entities.HistoryEntry
import com.shadaeiou.stitchcounter.data.db.entities.Project
import com.shadaeiou.stitchcounter.data.notes.NoteItem
import com.shadaeiou.stitchcounter.data.notes.parseNotes
import com.shadaeiou.stitchcounter.data.notes.toNotesJson
import com.shadaeiou.stitchcounter.data.repo.ProjectRepository
import com.shadaeiou.stitchcounter.ui.pdf.Stroke
import com.shadaeiou.stitchcounter.ui.pdf.StrokePoint
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

enum class Tool { None, Pen, Eraser }

class CounterViewModel(
    private val repository: ProjectRepository,
) : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _tool = MutableStateFlow(Tool.None)
    val tool: StateFlow<Tool> = _tool.asStateFlow()

    private val _penColorArgb = MutableStateFlow(0xFFEF4444L)  // red
    val penColorArgb: StateFlow<Long> = _penColorArgb.asStateFlow()

    private val _penWidthPx = MutableStateFlow(6f)
    val penWidthPx: StateFlow<Float> = _penWidthPx.asStateFlow()

    // Per-page redo stack for stroke undo/redo. Cleared when the page,
    // PDF, or strokes are mutated outside the undo/redo flow.
    private val _redoStack = MutableStateFlow<List<Stroke>>(emptyList())
    val canRedo: StateFlow<Boolean> = _redoStack
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val history: StateFlow<List<HistoryEntry>> = _project
        .flatMapLatest { p ->
            if (p == null) flow { emit(emptyList<HistoryEntry>()) }
            else repository.observeHistory(p.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notes: StateFlow<List<NoteItem>> = _project
        .map { parseNotes(it?.notes.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pinnedNotes: StateFlow<List<NoteItem>> = notes
        .map { it.filter { n -> n.pinned } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val knitPattern: StateFlow<String> = _project
        .map { it?.knitPattern.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val patternHtml: StateFlow<String> = _project
        .map { it?.patternHtml.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val patternHighlightRange: StateFlow<String> = _project
        .map { it?.patternHighlightRange.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _patternTool = MutableStateFlow(Tool.None)
    val patternTool: StateFlow<Tool> = _patternTool.asStateFlow()

    private val _patternRedoStack = MutableStateFlow<List<Stroke>>(emptyList())
    val canPatternRedo: StateFlow<Boolean> = _patternRedoStack
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Strokes for the pattern pane, stored at page = -1.
    val patternStrokes: StateFlow<List<Stroke>> = _project
        .map { p -> p?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flow { emit(emptyList<Stroke>()) }
            else repository.observeStrokes(id, -1)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val strokes: StateFlow<List<Stroke>> = _project
        .map { p -> p?.let { it.id to it.currentPage } }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            if (key == null) flow { emit(emptyList<Stroke>()) }
            else repository.observeStrokes(key.first, key.second)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _project.value = repository.ensureProject()
        }
    }

    fun increment() = viewModelScope.launch {
        if (_locked.value) return@launch
        val p = _project.value ?: return@launch
        _project.value = repository.increment(p)
    }

    fun decrement() = viewModelScope.launch {
        if (_locked.value) return@launch
        val p = _project.value ?: return@launch
        _project.value = repository.decrement(p)
    }

    fun reset() = viewModelScope.launch {
        val p = _project.value ?: return@launch
        _project.value = repository.reset(p)
    }

    fun undoLast() = viewModelScope.launch {
        val p = _project.value ?: return@launch
        _project.value = repository.undoLast(p)
    }

    fun setLabel(label: String) = viewModelScope.launch {
        val p = _project.value ?: return@launch
        _project.value = repository.setLabel(p, label)
    }

    fun setPdfPath(path: String?) = viewModelScope.launch {
        val p = _project.value ?: return@launch
        _redoStack.value = emptyList()
        _project.value = repository.setPdf(p, path)
    }

    fun setPage(page: Int) = viewModelScope.launch {
        val p = _project.value ?: return@launch
        if (p.currentPage == page) return@launch
        _redoStack.value = emptyList()
        _project.value = repository.setPage(p, page)
    }

    fun setNotes(notes: String) = viewModelScope.launch {
        val p = _project.value ?: return@launch
        _project.value = repository.setNotes(p, notes)
    }

    private fun saveNotesList(list: List<NoteItem>) = viewModelScope.launch {
        val p = _project.value ?: return@launch
        _project.value = repository.setNotes(p, list.toNotesJson())
    }

    fun addNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val current = notes.value
        saveNotesList(current + NoteItem(id = UUID.randomUUID().toString(), text = trimmed))
    }

    fun deleteNote(id: String) {
        val current = notes.value
        saveNotesList(current.filter { it.id != id })
    }

    fun togglePin(id: String) {
        val current = notes.value
        saveNotesList(current.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
    }

    fun updateNote(id: String, text: String) {
        val trimmed = text.trim()
        val current = notes.value
        saveNotesList(current.map { if (it.id == id) it.copy(text = trimmed) else it })
    }

    fun setKnitPattern(pattern: String) = viewModelScope.launch {
        val p = _project.value ?: return@launch
        _project.value = repository.setKnitPattern(p, pattern)
    }

    fun setPatternHtml(html: String) = viewModelScope.launch {
        val p = _project.value ?: return@launch
        _project.value = repository.setPatternHtml(p, html)
    }

    fun setPatternHighlightRange(range: String) = viewModelScope.launch {
        val p = _project.value ?: return@launch
        _project.value = repository.setPatternHighlightRange(p, range)
    }

    fun selectPatternTool(t: Tool) {
        _patternTool.value = if (_patternTool.value == t) Tool.None else t
    }

    fun addPatternStroke(points: List<StrokePoint>, colorArgb: Long, widthPx: Float) = viewModelScope.launch {
        if (points.size < 2) return@launch
        val p = _project.value ?: return@launch
        val current = patternStrokes.value
        _patternRedoStack.value = emptyList()
        repository.saveStrokes(p.id, -1, current + Stroke(points = points, colorArgb = colorArgb, widthPx = widthPx))
    }

    fun erasePatternAt(x: Float, y: Float, toleranceNorm: Float) = viewModelScope.launch {
        val p = _project.value ?: return@launch
        val current = patternStrokes.value
        if (current.isEmpty()) return@launch
        val tol2 = toleranceNorm.pow(2)
        val filtered = current.filter { stroke ->
            stroke.points.none { pt -> (pt.x - x).pow(2) + (pt.y - y).pow(2) < tol2 }
        }
        if (filtered.size != current.size) {
            _patternRedoStack.value = emptyList()
            repository.saveStrokes(p.id, -1, filtered)
        }
    }

    fun undoLastPatternStroke() = viewModelScope.launch {
        val p = _project.value ?: return@launch
        val current = patternStrokes.value
        if (current.isEmpty()) return@launch
        val last = current.last()
        _patternRedoStack.value = _patternRedoStack.value + last
        repository.saveStrokes(p.id, -1, current.dropLast(1))
    }

    fun redoLastPatternStroke() = viewModelScope.launch {
        val p = _project.value ?: return@launch
        val redo = _patternRedoStack.value
        if (redo.isEmpty()) return@launch
        val toRestore = redo.last()
        _patternRedoStack.value = redo.dropLast(1)
        repository.saveStrokes(p.id, -1, patternStrokes.value + toRestore)
    }

    fun toggleLock() {
        _locked.value = !_locked.value
    }

    fun selectTool(t: Tool) {
        _tool.value = if (_tool.value == t) Tool.None else t
    }

    fun setPenColor(argb: Long) { _penColorArgb.value = argb }
    fun setPenWidth(px: Float) { _penWidthPx.value = px.coerceIn(1f, 36f) }

    fun addStroke(points: List<StrokePoint>, colorArgb: Long, widthPx: Float) = viewModelScope.launch {
        if (points.size < 2) return@launch
        val p = _project.value ?: return@launch
        val current = strokes.value
        _redoStack.value = emptyList()
        repository.saveStrokes(
            p.id, p.currentPage,
            current + Stroke(points = points, colorArgb = colorArgb, widthPx = widthPx),
        )
    }

    fun eraseAt(x: Float, y: Float, toleranceNorm: Float) = viewModelScope.launch {
        val p = _project.value ?: return@launch
        val current = strokes.value
        if (current.isEmpty()) return@launch
        val tol2 = toleranceNorm.pow(2)
        val filtered = current.filter { stroke ->
            stroke.points.none { pt -> (pt.x - x).pow(2) + (pt.y - y).pow(2) < tol2 }
        }
        if (filtered.size != current.size) {
            _redoStack.value = emptyList()
            repository.saveStrokes(p.id, p.currentPage, filtered)
        }
    }

    fun undoLastStroke() = viewModelScope.launch {
        val p = _project.value ?: return@launch
        val current = strokes.value
        if (current.isEmpty()) return@launch
        val last = current.last()
        _redoStack.value = _redoStack.value + last
        repository.saveStrokes(p.id, p.currentPage, current.dropLast(1))
    }

    fun redoLastStroke() = viewModelScope.launch {
        val p = _project.value ?: return@launch
        val redo = _redoStack.value
        if (redo.isEmpty()) return@launch
        val toRestore = redo.last()
        _redoStack.value = redo.dropLast(1)
        repository.saveStrokes(p.id, p.currentPage, strokes.value + toRestore)
    }

    @Suppress("UNUSED_PARAMETER")
    fun strokeContains(stroke: Stroke, x: Float, y: Float, tolerance: Float): Boolean {
        val tol2 = tolerance.pow(2)
        return stroke.points.any { (it.x - x).pow(2) + (it.y - y).pow(2) < tol2 }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = StitchCounterApp.instance.repository
            return CounterViewModel(repo) as T
        }
    }
}
