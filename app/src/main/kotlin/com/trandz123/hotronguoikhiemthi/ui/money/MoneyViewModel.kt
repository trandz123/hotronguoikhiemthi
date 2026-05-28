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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private var idleReminderJob: Job? = null
    private var cooldownJob: Job? = null
    private var welcomeAnnounced = false

    /** Welcome khi vao MoneyScreen. */
    fun playWelcomeOnce() {
        if (welcomeAnnounced) return
        welcomeAnnounced = true
        viewModelScope.launch {
            tts.speak(WELCOME_MONEY)
            startIdleReminder()
        }
    }

    fun onFrameQuality(brightness: Double, sharpness: Double) {
        val s = _state.value
        if (s is MoneyUiState.Analyzing) {
            _state.value = MoneyUiState.Analyzing(brightness, sharpness)
            // Frame quality update — reset idle reminder timer khi co frame sang/sharp.
            if (brightness > 60.0 && sharpness > 60.0) {
                startIdleReminder()
            }
        }
    }

    /**
     * Goi tu MoneyScreen khi FrameQualityAnalyzer phat hien khung hinh on dinh
     * (HOAC user double-tap chu dong). [bitmapProvider] cung cap bitmap async tu capture.
     */
    fun onCapture(bitmapProvider: suspend () -> Bitmap) {
        if (_state.value !is MoneyUiState.Analyzing) return
        idleReminderJob?.cancel()
        _state.value = MoneyUiState.Capturing
        viewModelScope.launch {
            val bitmap = runCatching { bitmapProvider() }.getOrNull()
            if (bitmap == null) {
                speakError("Không chụp được, vui lòng thử lại.")
                _state.value = MoneyUiState.Analyzing(0.0, 0.0)
                startIdleReminder()
                return@launch
            }
            _state.value = MoneyUiState.Classifying
            val result = classifier.classify(bitmap)
            val recognized = (result as? MoneyResult.Recognized)
                ?.takeIf { it.confidence >= STRICT_CONFIDENCE }
            val spoken = if (recognized != null) {
                recognized.denominationVnd.toVietnameseMoney()
            } else {
                "Không nhận diện được, vui lòng thử lại."
            }
            _state.value = MoneyUiState.Result(result, spoken)
            tts.speak(spoken)
            if (recognized != null) {
                historyRepo.record(ScanType.MONEY, spoken)
                // Cooldown 3s sau khi nhan dien — tranh doc lap lai cung 1 to tien.
                startCooldown()
            } else {
                // Khong nhan duoc → reset ngay ve Analyzing de quet lai.
                delay(500L)
                _state.value = MoneyUiState.Analyzing(0.0, 0.0)
                startIdleReminder()
            }
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            delay(COOLDOWN_MS)
            _state.value = MoneyUiState.Analyzing(0.0, 0.0)
            startIdleReminder()
        }
    }

    /**
     * Sau IDLE_REMINDER_MS giay khong co frame chat luong on dinh → nhac nhu cau.
     */
    private fun startIdleReminder() {
        idleReminderJob?.cancel()
        idleReminderJob = viewModelScope.launch {
            delay(IDLE_REMINDER_MS)
            if (_state.value is MoneyUiState.Analyzing) {
                tts.speak("Không tìm thấy tiền, hãy đưa tờ tiền vào giữa camera.")
                // Khong loop — chi nhac mot lan, doi user di chuyen camera (frame quality update se reset).
            }
        }
    }

    /**
     * Quay lai trang thai Analyzing (sau khi nguoi dung muon quet tiep).
     */
    fun scanAgain() {
        cooldownJob?.cancel()
        tts.stop()
        _state.value = MoneyUiState.Analyzing(0.0, 0.0)
        startIdleReminder()
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

    override fun onCleared() {
        super.onCleared()
        idleReminderJob?.cancel()
        cooldownJob?.cancel()
    }

    private companion object {
        const val WELCOME_MONEY =
            "Đang chuyển sang chế độ nhận diện tiền Việt Nam."
        const val COOLDOWN_MS = 3_000L
        const val IDLE_REMINDER_MS = 5_000L
        /** Threshold strict cho UI — vuot qua nguong nay moi coi la "nhan duoc". */
        const val STRICT_CONFIDENCE = 0.75f
    }
}

sealed class MoneyUiState {
    data class Analyzing(val brightness: Double, val sharpness: Double) : MoneyUiState()
    data object Capturing : MoneyUiState()
    data object Classifying : MoneyUiState()
    data class Result(val moneyResult: MoneyResult, val spokenText: String) : MoneyUiState()
}
