package com.shadaeiou.stitchcounter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadaeiou.stitchcounter.StitchCounterApp
import com.shadaeiou.stitchcounter.data.db.entities.HistoryEntry
import com.shadaeiou.stitchcounter.data.db.entities.Project
import com.shadaeiou.stitchcounter.data.repo.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CounterViewModel(
    private val repository: ProjectRepository,
) : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    val history: StateFlow<List<HistoryEntry>> = _project
        .flatMapLatest { p ->
            if (p == null) flow { emit(emptyList<HistoryEntry>()) }
            else repository.observeHistory(p.id)
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
        _project.value = repository.setPdf(p, path)
    }

    fun setPage(page: Int) = viewModelScope.launch {
        val p = _project.value ?: return@launch
        if (p.currentPage == page) return@launch
        _project.value = repository.setPage(p, page)
    }

    fun toggleLock() {
        _locked.value = !_locked.value
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = StitchCounterApp.instance.repository
            return CounterViewModel(repo) as T
        }
    }
}
