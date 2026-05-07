package com.shadaeiou.stitchcounter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shadaeiou.stitchcounter.StitchCounterApp
import com.shadaeiou.stitchcounter.data.db.entities.KnitProject
import com.shadaeiou.stitchcounter.data.repo.KnitProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class KnitProjectViewModel(
    private val repository: KnitProjectRepository,
) : ViewModel() {

    val projects: StateFlow<List<KnitProject>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun create(): Long = repository.create()

    fun update(project: KnitProject) = viewModelScope.launch {
        repository.update(project)
    }

    fun delete(id: Long) = viewModelScope.launch {
        repository.delete(id)
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            KnitProjectViewModel(StitchCounterApp.instance.knitProjectRepository) as T
    }
}
