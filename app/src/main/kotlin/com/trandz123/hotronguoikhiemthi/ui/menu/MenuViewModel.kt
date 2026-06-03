package com.trandz123.hotronguoikhiemthi.ui.menu

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trandz123.hotronguoikhiemthi.data.history.HistoryRepository
import com.trandz123.hotronguoikhiemthi.data.history.ScanType
import com.trandz123.hotronguoikhiemthi.ml.GroqMenuAnalyzer
import com.trandz123.hotronguoikhiemthi.ml.MenuItem
import com.trandz123.hotronguoikhiemthi.ml.MenuOcrEngine
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

/**
 * Pipeline menu v0.5:
 *   1. Camera capture → Bitmap
 *   2. ML Kit OCR (on-device) → raw text
 *   3. Gemini Flash text-only → JSON items
 *   4. TTS doc lan luot
 *
 * Khi ML Kit khong tim duoc chu (raw text rong) → TTS nhac + auto countdown lai 3s.
 * Khi Gemini fail (mat mang/HTTP error) → TTS error message → reset Idle (user vuot len de thu lai).
 */
@HiltViewModel
class MenuViewModel @Inject constructor(
    private val ocrEngine: MenuOcrEngine,
    private val analyzer: GroqMenuAnalyzer,
    private val tts: TtsManager,
    private val historyRepo: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MenuUiState>(MenuUiState.Idle)
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    private var readingJob: Job? = null
    private var countdownJob: Job? = null
    private var welcomeJob: Job? = null
    private var welcomeAnnounced = false

    fun playWelcomeOnce() {
        if (welcomeAnnounced) return
        welcomeAnnounced = true
        welcomeJob = viewModelScope.launch {
            tts.stop()
            tts.speak(WELCOME_MENU)
        }
    }

    fun startCountdown(bitmapProvider: suspend () -> Bitmap) {
        if (_state.value !is MenuUiState.Idle) return
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            // Cho welcome speech doc xong truoc khi bat dau dem
            welcomeJob?.join()
            // Khoang nghi 500ms giua welcome va countdown -- tranh nuot tieng "Ba"
            delay(500L)
            // Audio countdown 3 giay -- nguoi mu nghe duoc luc nao chup
            for (i in 3 downTo 1) {
                if (_state.value !is MenuUiState.Idle && _state.value !is MenuUiState.CountingDown) {
                    return@launch
                }
                _state.value = MenuUiState.CountingDown(i)
                // Them dau cham de TTS doc ro -- so don le hay bi luot
                val word = when (i) { 3 -> "Ba."; 2 -> "Hai."; else -> "Một." }
                tts.speak(word)
                delay(200L)
            }
            if (_state.value is MenuUiState.CountingDown) {
                doCapture(bitmapProvider)
            }
        }
    }

    fun onCapture(bitmapProvider: suspend () -> Bitmap) {
        countdownJob?.cancel()
        viewModelScope.launch { doCapture(bitmapProvider) }
    }

    private suspend fun doCapture(bitmapProvider: suspend () -> Bitmap) {
        if (_state.value is MenuUiState.Reading) return
        _state.value = MenuUiState.Capturing
        val bitmap = runCatching { bitmapProvider() }.getOrNull()
        if (bitmap == null) {
            tts.stop()
            tts.speak("Không chụp được, vui lòng thử lại.")
            _state.value = MenuUiState.Idle
            return
        }

        // Pipeline v0.6: gui anh truc tiep len Groq Vision (Llama 4 Scout)
        // -- bo qua OCR vi ML Kit flatten layout cot khien LLM khong match dish-price duoc.
        _state.value = MenuUiState.GeminiParsing
        // Audio cue khi xu ly Vision API (mat 2-4 giay) -- nguoi mu khong tuong app treo
        val processingCueJob = viewModelScope.launch {
            delay(1_500L)
            tts.speak("Đang phân tích menu, vui lòng chờ")
        }
        val parseResult = runCatching { analyzer.parseMenuImage(bitmap) }
        processingCueJob.cancel()
        tts.stop()  // dung cue ngay khi API tra ket qua

        val ex = parseResult.exceptionOrNull()
        if (ex != null) {
            val msg = ex.message.orEmpty()
            val errorMsg = when {
                msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") ->
                    "Hệ thống đang quá tải, vui lòng đợi một phút rồi vuốt lên để thử lại."
                msg.contains("403") || msg.contains("API key") ->
                    "Lỗi cấu hình hệ thống, vui lòng kiểm tra lại."
                else -> NETWORK_ERROR_MSG
            }
            _state.value = MenuUiState.Error(msg)
            tts.speak(errorMsg)
            return
        }
        val items = parseResult.getOrDefault(emptyList())

        if (items.isEmpty()) {
            tts.speak("Không nhận diện được món nào, vuốt lên để thử lại.")
            _state.value = MenuUiState.Error("Empty menu")
            return
        }

        val summary = "Menu có ${items.size} món."
        _state.value = MenuUiState.Loaded(items = items, currentIndex = 0, selectedItems = emptyList())
        historyRepo.record(
            ScanType.MENU,
            summary + " " + items.take(5).joinToString("; ") { it.speakable() },
        )
        val first = items.first()
        readingJob?.cancel()
        readingJob = viewModelScope.launch {
            tts.speak(
                "$summary Món một: ${first.speakable()}. " +
                    "Vuốt phải sang món tiếp, vuốt xuống để chọn món."
            )
        }
    }

    fun nextItem() {
        val s = _state.value as? MenuUiState.Loaded ?: return
        if (s.currentIndex >= s.items.lastIndex) {
            readingJob?.cancel(); tts.stop()
            readingJob = viewModelScope.launch {
                tts.speak("Đã đến món cuối, ${s.items.last().speakable()}.")
            }
            return
        }
        val newIdx = s.currentIndex + 1
        readingJob?.cancel()
        readingJob = null
        tts.stop()
        _state.value = s.copy(currentIndex = newIdx)
        val prefix = ordinalVi(newIdx + 1)
        readingJob = viewModelScope.launch { tts.speak("$prefix ${s.items[newIdx].speakable()}") }
    }

    fun prevItem() {
        val s = _state.value as? MenuUiState.Loaded ?: return
        val newIdx = (s.currentIndex - 1).coerceAtLeast(0)
        readingJob?.cancel()
        readingJob = null
        tts.stop()
        _state.value = s.copy(currentIndex = newIdx)
        val prefix = ordinalVi(newIdx + 1)
        readingJob = viewModelScope.launch { tts.speak("$prefix ${s.items[newIdx].speakable()}") }
    }

    fun repeatCurrent() {
        val s = _state.value as? MenuUiState.Loaded ?: return
        readingJob?.cancel()
        readingJob = null
        tts.stop()
        readingJob = viewModelScope.launch { tts.speak(s.items[s.currentIndex].speakable()) }
    }

    /** Vuot xuong: chon mon hien tai. Idempotent — chon lai mon da co se thong bao. */
    fun selectCurrent() {
        val s = _state.value as? MenuUiState.Loaded ?: return
        val cur = s.items.getOrNull(s.currentIndex) ?: return
        readingJob?.cancel(); tts.stop()
        val alreadySelected = s.selectedItems.any { it.name == cur.name && it.priceVnd == cur.priceVnd }
        if (alreadySelected) {
            readingJob = viewModelScope.launch { tts.speak("Món ${cur.name} đã có trong danh sách rồi.") }
            return
        }
        val newSelected = s.selectedItems + cur
        _state.value = s.copy(selectedItems = newSelected)
        val total = newSelected.mapNotNull { it.priceVnd }.sum()
        val hasPrice = newSelected.any { it.priceVnd != null }
        val tail = if (hasPrice) "Tổng ${newSelected.size} món, ${total.toVietnameseWords()} đồng."
        else "Đã chọn ${newSelected.size} món."
        readingJob = viewModelScope.launch {
            tts.speak("Đã chọn ${cur.name}. $tail")
        }
    }

    /** Cham doi: nghe lai toan bo mon da chon + tong tien. */
    fun readSelected() {
        val s = _state.value as? MenuUiState.Loaded ?: return
        readingJob?.cancel(); tts.stop()
        if (s.selectedItems.isEmpty()) {
            readingJob = viewModelScope.launch {
                tts.speak("Chưa chọn món nào. Vuốt xuống để chọn món hiện tại.")
            }
            return
        }
        val total = s.selectedItems.mapNotNull { it.priceVnd }.sum()
        val hasPrice = s.selectedItems.any { it.priceVnd != null }
        val list = s.selectedItems.joinToString(", ") { it.name }
        val tail = if (hasPrice) " Tổng ${total.toVietnameseWords()} đồng." else ""
        readingJob = viewModelScope.launch {
            tts.speak("Đã chọn ${s.selectedItems.size} món: $list.$tail")
        }
    }

    fun stopReading() {
        readingJob?.cancel()
        readingJob = null
        tts.stop()
    }

    fun scanAgain() {
        readingJob?.cancel()
        countdownJob?.cancel()
        tts.stop()
        _state.value = MenuUiState.Idle
    }

    /** Goi khi roi MenuScreen. Reset state ve Idle de lan sau vao se scan moi. */
    fun stopAll() {
        readingJob?.cancel()
        countdownJob?.cancel()
        tts.stop()
        _state.value = MenuUiState.Idle
    }

    /** Thong bao chuyen mode + huy het audio cu + reset state. */
    fun switchToMoney() {
        readingJob?.cancel()
        countdownJob?.cancel()
        _state.value = MenuUiState.Idle
        viewModelScope.launch {
            tts.stop()
            tts.speak("Đang ở chế độ nhận diện tiền")
        }
    }

    private companion object {
        const val WELCOME_MENU =
            "Chế độ đọc menu. Hướng camera vào menu, giữ máy ổn định, tôi sẽ tự chụp."
        const val EMPTY_TEXT_MSG =
            "Không tìm thấy chữ trên menu, vui lòng đưa camera lại gần hơn."
        const val NETWORK_ERROR_MSG =
            "Có lỗi kết nối mạng. Hãy vuốt lên để thử lại menu hoặc vuốt xuống để chuyển sang chế độ nhận diện tiền."
    }
}

sealed class MenuUiState {
    data object Idle : MenuUiState()
    data class CountingDown(val secondsLeft: Int) : MenuUiState()
    data object Capturing : MenuUiState()
    /** ML Kit OCR dang chay tren bitmap. */
    data object OcrProcessing : MenuUiState()
    /** Da co raw text, dang gui Gemini parse. */
    data object GeminiParsing : MenuUiState()
    data class Loaded(
        val items: List<MenuItem>,
        val currentIndex: Int,
        val selectedItems: List<MenuItem> = emptyList(),
    ) : MenuUiState()

    data class Reading(val items: List<MenuItem>, val currentIndex: Int) : MenuUiState()
    data class Error(val message: String) : MenuUiState()
}

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
