package com.trandz123.hotronguoikhiemthi.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trandz123.hotronguoikhiemthi.data.history.HistoryRepository
import com.trandz123.hotronguoikhiemthi.data.history.ScanType
import com.trandz123.hotronguoikhiemthi.ml.MoneyResult
import com.trandz123.hotronguoikhiemthi.tts.TtsManager
import com.trandz123.hotronguoikhiemthi.util.toVietnameseMoney
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Live YOLO inference: nhan tu LiveMoneyAnalyzer mot stream MoneyResult, ap dung
 * stability filter + cooldown roi TTS doc menh gia.
 *
 *  - Stability: phai detect cung 1 denomination >= [REQUIRED_STABLE_FRAMES] frame lien tiep
 *    voi conf >= [STRICT_CONFIDENCE] truoc khi noi → khu false positive.
 *  - Cooldown: sau khi doc, im lang [COOLDOWN_MS]ms, khong doc lai (du tien con trong khung).
 *  - Idle reminder: neu ko thay tien sau [IDLE_REMINDER_MS]ms → nhac user dua tien vao giua.
 */
@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val tts: TtsManager,
    private val historyRepo: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MoneyUiState>(MoneyUiState.Analyzing)
    val state: StateFlow<MoneyUiState> = _state.asStateFlow()

    private var idleReminderJob: Job? = null
    private var cooldownJob: Job? = null
    private var welcomeAnnounced = false

    // Stability tracking
    private var lastDenomination: Long = 0L
    private var stableCount: Int = 0

    fun playWelcomeOnce() {
        if (welcomeAnnounced) return
        welcomeAnnounced = true
        viewModelScope.launch { startIdleReminder() }
    }

    /**
     * Goi tu LiveMoneyAnalyzer moi frame inference xong.
     * Trong cooldown → bo qua. Khong detect → reset stability.
     */
    fun onLiveDetection(result: MoneyResult) {
        // Trong cooldown — khong xu ly them
        if (_state.value is MoneyUiState.Result) return

        val recognized = (result as? MoneyResult.Recognized)
            ?.takeIf { it.confidence >= STRICT_CONFIDENCE }

        if (recognized == null) {
            // Khong nhan duoc → reset stability counter
            lastDenomination = 0L
            stableCount = 0
            return
        }

        val denomination = recognized.denominationVnd
        if (denomination == lastDenomination) {
            stableCount += 1
        } else {
            lastDenomination = denomination
            stableCount = 1
        }

        if (stableCount < REQUIRED_STABLE_FRAMES) return

        // Da on dinh → doc
        stableCount = 0
        idleReminderJob?.cancel()
        val spoken = denomination.toVietnameseMoney()
        _state.value = MoneyUiState.Result(recognized, spoken)
        viewModelScope.launch {
            tts.speak(spoken)
            historyRepo.record(ScanType.MONEY, spoken)
        }
        startCooldown()
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            delay(COOLDOWN_MS)
            lastDenomination = 0L
            stableCount = 0
            _state.value = MoneyUiState.Analyzing
            startIdleReminder()
        }
    }

    private fun startIdleReminder() {
        idleReminderJob?.cancel()
        idleReminderJob = viewModelScope.launch {
            delay(IDLE_REMINDER_MS)
            if (_state.value is MoneyUiState.Analyzing) {
                tts.speak("Không tìm thấy tiền, hãy đưa tờ tiền vào giữa camera.")
            }
        }
    }

    /** Vuot xuong de quet lai tu dau (huy cooldown). */
    fun scanAgain() {
        cooldownJob?.cancel()
        idleReminderJob?.cancel()
        tts.stop()
        lastDenomination = 0L
        stableCount = 0
        _state.value = MoneyUiState.Analyzing
        startIdleReminder()
    }

    /** Goi khi roi MoneyScreen (chuyen sang Menu). */
    fun stopAll() {
        cooldownJob?.cancel()
        idleReminderJob?.cancel()
        tts.stop()
    }

    /** Thong bao chuyen mode + huy het audio cu. */
    fun switchToMenu() {
        cooldownJob?.cancel()
        idleReminderJob?.cancel()
        viewModelScope.launch {
            tts.stop()
            tts.speak("Đang ở chế độ đọc menu")
        }
    }

    /** Doc lai noi dung cuoi cung. */
    fun repeatLast() {
        val s = _state.value
        if (s is MoneyUiState.Result) {
            viewModelScope.launch { tts.speak(s.spokenText) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        idleReminderJob?.cancel()
        cooldownJob?.cancel()
    }

    private companion object {
        const val WELCOME_MONEY =
            "Đang chuyển sang chế độ nhận diện tiền Việt Nam. Hãy đưa tờ tiền vào trước camera."
        const val COOLDOWN_MS = 3_000L
        const val IDLE_REMINDER_MS = 5_000L
        /** Toi uu speed: 1 frame stable, confidence cao -> doc ngay khi detect. */
        const val REQUIRED_STABLE_FRAMES = 1
        /** Threshold giam xuong 0.70 -- van loc duoc noise nhung doc som hon. */
        const val STRICT_CONFIDENCE = 0.70f
    }
}

sealed class MoneyUiState {
    data object Analyzing : MoneyUiState()
    data class Result(val moneyResult: MoneyResult, val spokenText: String) : MoneyUiState()
}
