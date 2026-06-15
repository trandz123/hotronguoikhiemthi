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
 * Money UX mirror voi Menu: live detect → TTS doc menh gia → user vuot xuong de chon.
 *
 * State:
 *   - currentDetectedBill: to dang nam trong khung va da on dinh, CHUA cong vao tong.
 *   - bills: danh sach cac to da chon (cong dồn).
 *
 * Anti-double-count: sau khi user vuot xuong chon 1 to → awaitingClear=true →
 *   phai thay >= REQUIRED_CLEAR_FRAMES frame "khong co tien" moi cho dem to ke tiep.
 */
@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val tts: TtsManager,
    private val historyRepo: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MoneyUiState(0L, 0, null))
    val state: StateFlow<MoneyUiState> = _state.asStateFlow()

    private var idleReminderJob: Job? = null
    private var speakJob: Job? = null
    private var welcomeAnnounced = false

    private var lastDenomination: Long = 0L
    private var stableCount: Int = 0
    private var currentDetectedBill: Long = 0L
    private var framesSinceLastDetection: Int = 0
    private var awaitingClear: Boolean = false
    private var clearFrameCount: Int = 0
    private var firstBillAnnounced: Boolean = false
    private val bills = mutableListOf<Long>()

    fun playWelcomeOnce() {
        if (welcomeAnnounced) return
        welcomeAnnounced = true
        speakJob = viewModelScope.launch {
            tts.stop()
            tts.speak(WELCOME_MSG)
        }
        startIdleReminder()
    }

    fun onLiveDetection(result: MoneyResult) {
        val recognized = (result as? MoneyResult.Recognized)
            ?.takeIf { it.confidence >= STRICT_CONFIDENCE }

        if (recognized == null) {
            framesSinceLastDetection += 1
            if (awaitingClear) {
                clearFrameCount += 1
                if (clearFrameCount >= REQUIRED_CLEAR_FRAMES) {
                    awaitingClear = false
                    clearFrameCount = 0
                }
            }
            if (framesSinceLastDetection >= CLEAR_THRESHOLD && currentDetectedBill != 0L) {
                currentDetectedBill = 0L
                _state.value = _state.value.copy(currentBill = null)
                lastDenomination = 0L
                stableCount = 0
            }
            return
        }

        framesSinceLastDetection = 0
        val denomination = recognized.denominationVnd
        if (awaitingClear) {
            val lastSelected = bills.lastOrNull() ?: 0L
            if (denomination != lastSelected) {
                // Menh gia khac → chac chan to moi, bypass anti-double-count
                awaitingClear = false
                clearFrameCount = 0
            } else {
                // Cung menh gia voi to vua chon → cho user nhac ra
                clearFrameCount = 0
                return
            }
        }
        if (denomination == lastDenomination) stableCount += 1
        else {
            lastDenomination = denomination
            stableCount = 1
        }
        if (stableCount < REQUIRED_STABLE_FRAMES) return

        // Tờ mới (khác với tờ đang được track) → announce
        if (denomination != currentDetectedBill) {
            currentDetectedBill = denomination
            _state.value = _state.value.copy(currentBill = denomination)
            idleReminderJob?.cancel()

            val isFirst = !firstBillAnnounced
            firstBillAnnounced = true
            val base = "Tờ ${denomination.toVietnameseMoney()}"
            val msg = if (isFirst) "$base. $FIRST_BILL_GUIDE" else base
            speakJob?.cancel()
            speakJob = viewModelScope.launch {
                tts.stop()
                tts.speak(msg)
            }
        }
    }

    /** Vuot xuong: cong to dang detect vao tong (giong vuot xuong chon mon o menu). */
    fun selectCurrent() {
        if (currentDetectedBill == 0L) {
            speakJob?.cancel()
            speakJob = viewModelScope.launch {
                tts.stop()
                tts.speak("Chưa thấy tờ tiền nào, hãy đưa tờ tiền vào.")
            }
            return
        }
        val bill = currentDetectedBill
        bills.add(bill)
        val total = bills.sum()
        currentDetectedBill = 0L
        awaitingClear = true
        clearFrameCount = 0
        lastDenomination = 0L
        stableCount = 0
        _state.value = MoneyUiState(total = total, billCount = bills.size, currentBill = null)
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            tts.stop()
            tts.speak(
                "Đã chọn ${bill.toVietnameseMoney()}. " +
                    "Tổng ${bills.size} tờ, ${total.toVietnameseMoney()}.",
            )
        }
    }

    /** Cham doi: doc tong + danh sach da chon (giong cham doi o menu). */
    fun readSelected() {
        speakJob?.cancel()
        if (bills.isEmpty()) {
            speakJob = viewModelScope.launch {
                tts.stop()
                tts.speak("Chưa chọn tờ nào, vuốt xuống để chọn tờ đang thấy.")
            }
            return
        }
        val list = bills.joinToString(", ") { it.toVietnameseMoney() }
        val total = bills.sum()
        speakJob = viewModelScope.launch {
            tts.stop()
            tts.speak("Đã chọn ${bills.size} tờ: $list. Tổng ${total.toVietnameseMoney()}.")
        }
    }

    /** Giu lau: xoa het, dem lai (giong giu lau o menu = quet lai). */
    fun scanAgain() {
        bills.clear()
        lastDenomination = 0L
        stableCount = 0
        currentDetectedBill = 0L
        framesSinceLastDetection = 0
        awaitingClear = false
        clearFrameCount = 0
        firstBillAnnounced = false
        _state.value = MoneyUiState(0L, 0, null)
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            tts.stop()
            tts.speak("Đã xóa, đếm lại từ đầu.")
        }
        startIdleReminder()
    }

    private fun startIdleReminder() {
        idleReminderJob?.cancel()
        idleReminderJob = viewModelScope.launch {
            delay(IDLE_REMINDER_MS)
            if (currentDetectedBill == 0L && bills.isEmpty()) {
                tts.speak("Đưa tờ tiền vào giữa camera.")
            }
        }
    }

    /** Goi khi roi MoneyScreen. */
    fun stopAll() {
        speakJob?.cancel()
        idleReminderJob?.cancel()
        tts.stop()
    }

    /** Vuot len: chuyen sang menu — luu tong vao history truoc khi reset. */
    fun switchToMenu() {
        speakJob?.cancel()
        idleReminderJob?.cancel()
        val hadBills = bills.isNotEmpty()
        val savedTotal = bills.sum()
        val savedCount = bills.size
        bills.clear()
        currentDetectedBill = 0L
        lastDenomination = 0L
        stableCount = 0
        awaitingClear = false
        clearFrameCount = 0
        framesSinceLastDetection = 0
        firstBillAnnounced = false
        _state.value = MoneyUiState(0L, 0, null)
        speakJob = viewModelScope.launch {
            tts.stop()
            if (hadBills) {
                historyRepo.record(
                    ScanType.MONEY,
                    "Tổng $savedCount tờ: ${savedTotal.toVietnameseMoney()}",
                )
            }
            tts.speak("Đang ở chế độ đọc menu")
        }
    }

    override fun onCleared() {
        super.onCleared()
        idleReminderJob?.cancel()
        speakJob?.cancel()
    }

    private companion object {
        const val WELCOME_MSG =
            "Chế độ chọn tiền. Đưa tờ tiền vào trước camera, " +
                "tôi sẽ đọc mệnh giá. Vuốt xuống để chọn tờ đó."
        /** Phat sau khi to dau tien duoc nhan dien — nhac gesture chinh. */
        const val FIRST_BILL_GUIDE =
            "Vuốt xuống để chọn tờ này. Chạm đôi nghe tổng đã chọn. " +
                "Giữ lâu để xóa và đếm lại. Vuốt lên sang menu."
        const val IDLE_REMINDER_MS = 6_000L
        const val REQUIRED_STABLE_FRAMES = 1
        const val STRICT_CONFIDENCE = 0.70f
        /** Sau khi chon 1 to CUNG MENH GIA, doi user nhac ra >= 2 frame moi nhan to tiep. */
        const val REQUIRED_CLEAR_FRAMES = 2
        /** Sau >= 3 frame khong thay tien thi clear currentDetectedBill. */
        const val CLEAR_THRESHOLD = 3
    }
}

data class MoneyUiState(
    val total: Long,
    val billCount: Int,
    val currentBill: Long?,
)
