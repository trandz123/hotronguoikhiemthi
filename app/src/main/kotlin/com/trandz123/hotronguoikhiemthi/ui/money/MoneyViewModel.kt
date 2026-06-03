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
 * Live YOLO inference voi 2 mode:
 *
 *  - NORMAL: doc 1 to tien, cooldown 3s (UX cu).
 *  - COUNTING: cong don nhieu to. Anti-double-count = phai thay >= REQUIRED_CLEAR_FRAMES
 *    frame "khong co tien" giua hai lan add → ep user nhac to cu ra khoi khung.
 */
@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val tts: TtsManager,
    private val historyRepo: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MoneyUiState>(MoneyUiState.Analyzing)
    val state: StateFlow<MoneyUiState> = _state.asStateFlow()

    private val _mode = MutableStateFlow(Mode.NORMAL)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _counting = MutableStateFlow(CountingState())
    val counting: StateFlow<CountingState> = _counting.asStateFlow()

    private var idleReminderJob: Job? = null
    private var cooldownJob: Job? = null
    private var welcomeAnnounced = false

    // Stability tracking (dung cho ca 2 mode)
    private var lastDenomination: Long = 0L
    private var stableCount: Int = 0

    // COUNTING mode internal state
    private var awaitingClear: Boolean = false
    private var clearFrameCount: Int = 0
    private var lastAddedDenomination: Long = 0L
    private val bills = mutableListOf<Long>()

    fun playWelcomeOnce() {
        if (welcomeAnnounced) return
        welcomeAnnounced = true
        viewModelScope.launch { startIdleReminder() }
    }

    fun onLiveDetection(result: MoneyResult) {
        when (_mode.value) {
            Mode.NORMAL -> handleNormal(result)
            Mode.COUNTING -> handleCounting(result)
        }
    }

    private fun handleNormal(result: MoneyResult) {
        if (_state.value is MoneyUiState.Result) return

        val recognized = (result as? MoneyResult.Recognized)
            ?.takeIf { it.confidence >= STRICT_CONFIDENCE }

        if (recognized == null) {
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

    private fun handleCounting(result: MoneyResult) {
        val recognized = (result as? MoneyResult.Recognized)
            ?.takeIf { it.confidence >= STRICT_CONFIDENCE }

        if (recognized == null) {
            if (awaitingClear) {
                clearFrameCount += 1
                if (clearFrameCount >= REQUIRED_CLEAR_FRAMES) {
                    awaitingClear = false
                    clearFrameCount = 0
                }
            }
            lastDenomination = 0L
            stableCount = 0
            return
        }

        if (awaitingClear) {
            // To cu (hoac to moi) van trong khung — cho user nhac ra
            clearFrameCount = 0
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

        bills.add(denomination)
        lastAddedDenomination = denomination
        val newTotal = bills.sum()
        awaitingClear = true
        clearFrameCount = 0
        stableCount = 0

        _counting.value = CountingState(total = newTotal, billCount = bills.size, lastAdded = denomination)
        _state.value = MoneyUiState.Counting(newTotal, bills.size)

        viewModelScope.launch {
            tts.speak("Cộng ${denomination.toVietnameseMoney()}. Tổng ${newTotal.toVietnameseMoney()}.")
        }
    }

    fun enterCountingMode() {
        if (_mode.value == Mode.COUNTING) return
        _mode.value = Mode.COUNTING
        bills.clear()
        lastAddedDenomination = 0L
        awaitingClear = false
        clearFrameCount = 0
        stableCount = 0
        lastDenomination = 0L
        cooldownJob?.cancel()
        idleReminderJob?.cancel()
        _counting.value = CountingState()
        _state.value = MoneyUiState.Counting(0L, 0)
        viewModelScope.launch {
            tts.stop()
            tts.speak(
                "Chế độ đếm cộng dồn. Đưa từng tờ tiền vào, nhấc ra rồi đưa tờ kế tiếp. " +
                    "Vuốt lên nghe tổng. Vuốt xuống xóa. Vuốt trái thoát.",
            )
        }
    }

    fun exitCountingMode() {
        if (_mode.value != Mode.COUNTING) return
        _mode.value = Mode.NORMAL
        val finalTotal = bills.sum()
        val finalCount = bills.size
        bills.clear()
        lastAddedDenomination = 0L
        awaitingClear = false
        clearFrameCount = 0
        stableCount = 0
        lastDenomination = 0L
        _counting.value = CountingState()
        _state.value = MoneyUiState.Analyzing
        viewModelScope.launch {
            tts.stop()
            if (finalCount > 0) {
                val msg = "Thoát chế độ đếm. Tổng $finalCount tờ, ${finalTotal.toVietnameseMoney()}."
                tts.speak(msg)
                historyRepo.record(ScanType.MONEY, msg)
            } else {
                tts.speak("Thoát chế độ đếm. Chưa cộng tờ nào.")
            }
            startIdleReminder()
        }
    }

    fun readTotal() {
        if (_mode.value != Mode.COUNTING) return
        val total = bills.sum()
        val count = bills.size
        viewModelScope.launch {
            tts.stop()
            if (count == 0) {
                tts.speak("Chưa cộng tờ nào, hãy đưa tờ tiền vào trước.")
            } else {
                tts.speak("Tổng $count tờ, ${total.toVietnameseMoney()}.")
            }
        }
    }

    fun resetCount() {
        if (_mode.value != Mode.COUNTING) return
        bills.clear()
        lastAddedDenomination = 0L
        awaitingClear = false
        clearFrameCount = 0
        stableCount = 0
        lastDenomination = 0L
        _counting.value = CountingState()
        _state.value = MoneyUiState.Counting(0L, 0)
        viewModelScope.launch {
            tts.stop()
            tts.speak("Đã xóa, bắt đầu đếm lại.")
        }
    }

    fun repeatLastAdded() {
        if (_mode.value != Mode.COUNTING) return
        val last = lastAddedDenomination
        viewModelScope.launch {
            tts.stop()
            if (last == 0L) {
                tts.speak("Chưa có tờ nào để lặp lại.")
            } else {
                tts.speak("Tờ vừa cộng: ${last.toVietnameseMoney()}. Tổng ${bills.sum().toVietnameseMoney()}.")
            }
        }
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
            if (_state.value is MoneyUiState.Analyzing && _mode.value == Mode.NORMAL) {
                tts.speak("Không tìm thấy tiền, hãy đưa tờ tiền vào giữa camera.")
            }
        }
    }

    /** Vuot xuong de quet lai tu dau (NORMAL mode) — huy cooldown. */
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
        val wasCounting = _mode.value == Mode.COUNTING && bills.isNotEmpty()
        val savedTotal = if (wasCounting) bills.sum() else 0L
        val savedCount = if (wasCounting) bills.size else 0
        _mode.value = Mode.NORMAL
        bills.clear()
        _counting.value = CountingState()
        viewModelScope.launch {
            tts.stop()
            if (wasCounting) {
                historyRepo.record(ScanType.MONEY, "Tổng $savedCount tờ: ${savedTotal.toVietnameseMoney()}")
            }
            tts.speak("Đang ở chế độ đọc menu")
        }
    }

    /** Doc lai noi dung cuoi cung (NORMAL mode). */
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
        const val REQUIRED_STABLE_FRAMES = 1
        const val STRICT_CONFIDENCE = 0.70f
        /** So frame "khong co tien" lien tiep can thay → coi nhu user da nhac to cu ra. */
        const val REQUIRED_CLEAR_FRAMES = 5
    }
}

enum class Mode { NORMAL, COUNTING }

data class CountingState(
    val total: Long = 0L,
    val billCount: Int = 0,
    val lastAdded: Long? = null,
)

sealed class MoneyUiState {
    data object Analyzing : MoneyUiState()
    data class Result(val moneyResult: MoneyResult, val spokenText: String) : MoneyUiState()
    data class Counting(val total: Long, val billCount: Int) : MoneyUiState()
}
