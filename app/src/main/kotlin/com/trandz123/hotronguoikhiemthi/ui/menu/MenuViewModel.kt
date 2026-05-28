package com.trandz123.hotronguoikhiemthi.ui.menu

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trandz123.hotronguoikhiemthi.data.history.HistoryRepository
import com.trandz123.hotronguoikhiemthi.data.history.ScanType
import com.trandz123.hotronguoikhiemthi.ml.GeminiMenuAnalyzer
import com.trandz123.hotronguoikhiemthi.ml.MenuItem
import com.trandz123.hotronguoikhiemthi.tts.TtsManager
import com.trandz123.hotronguoikhiemthi.util.toVietnameseWords
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val analyzer: GeminiMenuAnalyzer,
    private val tts: TtsManager,
    private val historyRepo: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MenuUiState>(MenuUiState.Idle)
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    private var readingJob: Job? = null
    private var countdownJob: Job? = null
    private var welcomeAnnounced = false

    /** Welcome khi vao MenuScreen lan dau. Goi tu LaunchedEffect cua Screen. */
    fun playWelcomeOnce() {
        if (welcomeAnnounced) return
        welcomeAnnounced = true
        viewModelScope.launch {
            tts.speak(WELCOME_MENU)
        }
    }

    /**
     * Bat dem nguoc 3s roi tu dong chup. Goi tu MenuScreen khi vao trang thai Idle
     * va camera da san sang.
     */
    fun startCountdown(bitmapProvider: suspend () -> Bitmap) {
        if (_state.value !is MenuUiState.Idle) return
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _state.value = MenuUiState.CountingDown(3)
            delay(2_700L)
            _state.value = MenuUiState.CountingDown(0)
            tts.speak("Đang lấy nét và chụp ảnh.")
            delay(300L)
            if (_state.value is MenuUiState.CountingDown) {
                doCapture(bitmapProvider)
            }
        }
    }

    /** User chu dong chup bang double-tap, bo qua countdown. */
    fun onCapture(bitmapProvider: suspend () -> Bitmap) {
        countdownJob?.cancel()
        viewModelScope.launch { doCapture(bitmapProvider) }
    }

    private suspend fun doCapture(bitmapProvider: suspend () -> Bitmap) {
        if (_state.value is MenuUiState.Reading) return
        _state.value = MenuUiState.Capturing
        val bitmap = runCatching { bitmapProvider() }.getOrNull()
        if (bitmap == null) {
            tts.speak("Không chụp được, vui lòng thử lại.")
            _state.value = MenuUiState.Idle
            return
        }
        _state.value = MenuUiState.Processing
        val items = runCatching { analyzer.analyze(bitmap) }
            .onFailure { e ->
                _state.value = MenuUiState.Error(e.message ?: "Lỗi mạng")
                tts.speak(NETWORK_ERROR_MSG)
                return
            }
            .getOrThrow()
        if (items.isEmpty()) {
            tts.speak("Không tìm thấy món nào trên menu, vui lòng vuốt lên để chụp lại.")
            _state.value = MenuUiState.Idle
            return
        }
        val summary = "Menu có ${items.size} món."
        _state.value = MenuUiState.Loaded(items = items, currentIndex = 0, mode = ReadMode.IDLE)
        tts.speak(summary)
        historyRepo.record(ScanType.MENU, summary + " " + items.take(5).joinToString("; ") { it.speakable() })
        // Tu dong doc toan bo sau khi load
        readAllInternal()
    }

    private fun readAllInternal() {
        val s = _state.value as? MenuUiState.Loaded ?: return
        readingJob?.cancel()
        readingJob = viewModelScope.launch {
            _state.value = s.copy(mode = ReadMode.READING_ALL)
            for ((i, item) in s.items.withIndex()) {
                val cur = _state.value as? MenuUiState.Loaded ?: break
                if (cur.mode != ReadMode.READING_ALL) break
                _state.value = cur.copy(currentIndex = i, mode = ReadMode.READING_ALL)
                val prefix = ordinalVi(i + 1)
                tts.speak("$prefix ${item.speakable()}")
            }
            (_state.value as? MenuUiState.Loaded)?.let {
                _state.value = it.copy(mode = ReadMode.IDLE)
            }
        }
    }

    fun readAll() = readAllInternal()

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

    /** Vuot len → chup lai menu. Reset ve Idle de Screen kich hoat countdown moi. */
    fun scanAgain() {
        readingJob?.cancel()
        countdownJob?.cancel()
        tts.stop()
        _state.value = MenuUiState.Idle
    }

    private companion object {
        const val WELCOME_MENU =
            "Đang ở chế độ đọc menu. Hãy hướng camera về phía menu và giữ im máy trong ba giây."
        const val NETWORK_ERROR_MSG =
            "Có lỗi kết nối mạng. Hãy vuốt lên để thử lại menu hoặc vuốt xuống để chuyển sang chế độ nhận diện tiền."
    }
}

sealed class MenuUiState {
    data object Idle : MenuUiState()
    data class CountingDown(val secondsLeft: Int) : MenuUiState()
    data object Capturing : MenuUiState()
    data object Processing : MenuUiState()
    data class Loaded(
        val items: List<MenuItem>,
        val currentIndex: Int,
        val mode: ReadMode,
    ) : MenuUiState()

    /** Trang thai trong giua khi reading job dang chay (giu de tuong thich). */
    data class Reading(val items: List<MenuItem>, val currentIndex: Int) : MenuUiState()

    data class Error(val message: String) : MenuUiState()
}

enum class ReadMode { IDLE, READING_ALL }

private fun MenuItem.speakable(): String = when {
    priceVnd != null -> "$name, giá ${priceVnd.toVietnameseWords()} đồng"
    else -> name
}

private fun ordinalVi(n: Int): String = when (n) {
    1 -> "Món một:"
    2 -> "Món hai:"
    3 -> "Món ba:"
    4 -> "Món bốn:"
    5 -> "Món năm:"
    6 -> "Món sáu:"
    7 -> "Món bảy:"
    8 -> "Món tám:"
    9 -> "Món chín:"
    10 -> "Món mười:"
    else -> "Món $n:"
}
