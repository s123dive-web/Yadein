package com.yaadein.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Picks a random memory for the shake gesture. */
@HiltViewModel
class RandomMemoryViewModel @Inject constructor(private val memoryRepository: MemoryRepository) : ViewModel() {
    fun pick(onPicked: (Long) -> Unit) {
        viewModelScope.launch { memoryRepository.random()?.let { onPicked(it.memory.id) } }
    }
}
