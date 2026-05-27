package com.trandz123.hotronguoikhiemthi.ui.money

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trandz123.hotronguoikhiemthi.data.history.HistoryRepository
import com.trandz123.hotronguoikhiemthi.data.history.ScanType
import com.trandz123.hotronguoikhiemthi.data.settings.PreferencesRepository
import com.trandz123.hotronguoikhiemthi.ml.MoneyClassifier
import com.trandz123.hotronguoikhiemthi.ml.MoneyResult
import com.trandz123.hotronguoikhiemthi.tts.TtsManager
import com.trandz123.hotronguoikhiemthi.util.toVietnameseMoney
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val classifier: MoneyClassifier,
    private val tts: TtsManager,
    private val historyRepo: HistoryRepository,
    private val prefsRepo: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MoneyUiState>(MoneyUiState.Analyzing(0.0, 0.0))
    val state: StateFlow<MoneyUiState> = _state.asStateFlow()

    fun onFrameQuality(brightness: Double, sharpness: Double) {
        val s = _state.value
        if (s is MoneyUiState.Analyzing) {
            _state.value = MoneyUiState.Analyzing(brightness, sharpness)
        }
    }

    /**
     * Goi tu MoneyScreen khi FrameQualityAnalyzer phat hien khung hinh on dinh
     * (HOAC user double-tap chu dong). [bitmapProvider] cung cap bitmap async tu capture.
     */
    fun onCapture(bitmapProvider: suspend () -> Bitmap) {
        if (_state.value !is MoneyUiState.Analyzing) return
        _state.value = MoneyUiState.Capturing
        viewModelScope.launch {
            val bitmap = runCatching { bitmapProvider() }.getOrNull()
            if (bitmap == null) {
                speakError("Không chụp được, vui lòng thử lại")
                _state.value = MoneyUiState.Analyzing(0.0, 0.0)
                return@launch
            }
            _state.value = MoneyUiState.Classifying
            val result = classifier.classify(bitmap)
            val spoken = when (result) {
                is MoneyResult.Recognized -> result.denominationVnd.toVietnameseMoney()
                MoneyResult.Unknown -> "Không nhận diện được, vui lòng thử lại"
            }
            _state.value = MoneyUiState.Result(result, spoken)
            tts.speak(spoken)
            if (result is MoneyResult.Recognized) {
                historyRepo.record(ScanType.MONEY, spoken)
            }
        }
    }

    fun isAutoCaptureEnabled(): Boolean = runCatching {
        // Sync read fallback — neu coroutine chua start: tra true (default ON
        // cho money screen vi user khiem thi can hands-free).
        true
    }.getOrDefault(true)

    /**
     * Quay lai trang thai Analyzing (sau khi nguoi dung muon quet tiep).
     */
    fun scanAgain() {
        tts.stop()
        _state.value = MoneyUiState.Analyzing(0.0, 0.0)
    }

    /** Doc lai noi dung cuoi cung. */
    fun repeatLast() {
        val s = _state.value
        if (s is MoneyUiState.Result) {
            viewModelScope.launch { tts.speak(s.spokenText) }
        }
    }

    private fun speakError(msg: String) {
        viewModelScope.launch { tts.speak(msg) }
    }

    /** Doc setting auto-capture mot lan luc bat dau (suspend). */
    suspend fun resolveAutoCapture(): Boolean = prefsRepo.flow.first().autoCaptureEnabled
}

sealed class MoneyUiState {
    data class Analyzing(val brightness: Double, val sharpness: Double) : MoneyUiState()
    data object Capturing : MoneyUiState()
    data object Classifying : MoneyUiState()
    data class Result(val moneyResult: MoneyResult, val spokenText: String) : MoneyUiState()
}
