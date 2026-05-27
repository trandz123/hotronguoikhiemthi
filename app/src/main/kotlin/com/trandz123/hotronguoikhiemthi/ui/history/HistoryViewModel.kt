package com.trandz123.hotronguoikhiemthi.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trandz123.hotronguoikhiemthi.data.history.HistoryRepository
import com.trandz123.hotronguoikhiemthi.data.history.ScanHistoryEntity
import com.trandz123.hotronguoikhiemthi.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: HistoryRepository,
    private val tts: TtsManager,
) : ViewModel() {

    val recent: StateFlow<List<ScanHistoryEntity>> = repo.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun replay(entity: ScanHistoryEntity) {
        viewModelScope.launch { tts.speak(entity.spokenText) }
    }

    fun clearAll() {
        viewModelScope.launch { repo.clear() }
    }
}
