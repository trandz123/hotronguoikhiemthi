package com.trandz123.hotronguoikhiemthi.ui.menu

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trandz123.hotronguoikhiemthi.data.history.HistoryRepository
import com.trandz123.hotronguoikhiemthi.data.history.ScanType
import com.trandz123.hotronguoikhiemthi.ml.MenuItem
import com.trandz123.hotronguoikhiemthi.ml.MenuOcrEngine
import com.trandz123.hotronguoikhiemthi.tts.TtsManager
import com.trandz123.hotronguoikhiemthi.util.toVietnameseWords
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val ocrEngine: MenuOcrEngine,
    private val tts: TtsManager,
    private val historyRepo: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MenuUiState>(MenuUiState.Idle)
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    private var readingJob: Job? = null

    fun onCapture(bitmapProvider: suspend () -> Bitmap) {
        if (_state.value is MenuUiState.Reading) return
        _state.value = MenuUiState.Capturing
        viewModelScope.launch {
            val bitmap = runCatching { bitmapProvider() }.getOrNull()
            if (bitmap == null) {
                tts.speak("Không chụp được, vui lòng thử lại")
                _state.value = MenuUiState.Idle
                return@launch
            }
            _state.value = MenuUiState.Processing
            val items = runCatching { ocrEngine.extract(bitmap) }.getOrDefault(emptyList())
            if (items.isEmpty()) {
                tts.speak("Không tìm thấy văn bản, vui lòng đưa camera đến gần hơn")
                _state.value = MenuUiState.Idle
                return@launch
            }
            val summary = "Đã tìm thấy ${items.size} mục. Vuốt phải để nghe từng món, hoặc chạm đôi để nghe toàn bộ"
            _state.value = MenuUiState.Loaded(items = items, currentIndex = 0, mode = ReadMode.IDLE)
            tts.speak(summary)
            historyRepo.record(ScanType.MENU, summary + ". " + items.take(3).joinToString("; ") { it.speakable() })
        }
    }

    fun readAll() {
        val s = _state.value as? MenuUiState.Loaded ?: return
        readingJob?.cancel()
        readingJob = viewModelScope.launch {
            _state.value = s.copy(mode = ReadMode.READING_ALL)
            for ((i, item) in s.items.withIndex()) {
                if (_state.value !is MenuUiState.Reading && _state.value !is MenuUiState.Loaded) break
                _state.value = (_state.value as MenuUiState.Loaded).copy(currentIndex = i, mode = ReadMode.READING_ALL)
                tts.speak(item.speakable())
            }
            (_state.value as? MenuUiState.Loaded)?.let {
                _state.value = it.copy(mode = ReadMode.IDLE)
            }
        }
    }

    fun nextItem() {
        val s = _state.value as? MenuUiState.Loaded ?: return
        val newIdx = (s.currentIndex + 1).coerceAtMost(s.items.lastIndex)
        _state.value = s.copy(currentIndex = newIdx, mode = ReadMode.IDLE)
        viewModelScope.launch { tts.speak(s.items[newIdx].speakable()) }
    }

    fun prevItem() {
        val s = _state.value as? MenuUiState.Loaded ?: return
        val newIdx = (s.currentIndex - 1).coerceAtLeast(0)
        _state.value = s.copy(currentIndex = newIdx, mode = ReadMode.IDLE)
        viewModelScope.launch { tts.speak(s.items[newIdx].speakable()) }
    }

    fun repeatCurrent() {
        val s = _state.value as? MenuUiState.Loaded ?: return
        viewModelScope.launch { tts.speak(s.items[s.currentIndex].speakable()) }
    }

    fun stopReading() {
        readingJob?.cancel()
        readingJob = null
        tts.stop()
        (_state.value as? MenuUiState.Loaded)?.let { _state.value = it.copy(mode = ReadMode.IDLE) }
    }

    fun scanAgain() {
        readingJob?.cancel()
        tts.stop()
        _state.value = MenuUiState.Idle
    }
}

sealed class MenuUiState {
    data object Idle : MenuUiState()
    data object Capturing : MenuUiState()
    data object Processing : MenuUiState()
    data class Loaded(
        val items: List<MenuItem>,
        val currentIndex: Int,
        val mode: ReadMode,
    ) : MenuUiState()

    /** Trang thai trong giua khi reading job dang chay. */
    data class Reading(val items: List<MenuItem>, val currentIndex: Int) : MenuUiState()
}

enum class ReadMode { IDLE, READING_ALL }

private fun MenuItem.speakable(): String = when {
    priceVnd != null -> "$name, giá ${priceVnd.toVietnameseWords()} đồng"
    else -> name
}
