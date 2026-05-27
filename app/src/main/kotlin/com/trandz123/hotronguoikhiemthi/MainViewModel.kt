package com.trandz123.hotronguoikhiemthi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trandz123.hotronguoikhiemthi.data.settings.PreferencesRepository
import com.trandz123.hotronguoikhiemthi.tts.TtsManager
import com.trandz123.hotronguoikhiemthi.voice.VoiceCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State + event holder cho Activity. Su dung MainActivity de:
 *  - Trigger welcome greeting o lan dau onResume
 *  - Phat shake-event → goi tts.stop()
 *  - Phat voice command → emit [navEvents] cho NavHost subscribe
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val tts: TtsManager,
    private val prefsRepo: PreferencesRepository,
) : ViewModel() {

    private val _navEvents = MutableSharedFlow<NavEvent>(extraBufferCapacity = 4)
    val navEvents: SharedFlow<NavEvent> = _navEvents.asSharedFlow()

    private var welcomeSpoken = false

    fun playWelcomeOnce() {
        if (welcomeSpoken) return
        welcomeSpoken = true
        viewModelScope.launch {
            // Apply rate hien tai truoc khi noi
            val prefs = prefsRepo.flow.first()
            tts.setRate(prefs.ttsRate)
            tts.speak(WELCOME_TEXT)
        }
    }

    fun onShakeStopTts() {
        tts.stop()
    }

    fun onVoiceCommand(cmd: VoiceCommand) {
        when (cmd) {
            VoiceCommand.READ_MONEY -> emit(NavEvent.GoMoney)
            VoiceCommand.READ_MENU -> emit(NavEvent.GoMenu)
            VoiceCommand.HISTORY -> emit(NavEvent.GoHistory)
            VoiceCommand.EXIT -> emit(NavEvent.GoHome)
            VoiceCommand.STOP -> tts.stop()
            VoiceCommand.REPEAT -> emit(NavEvent.Repeat)
            VoiceCommand.RATE_UP -> adjustRate(0.2f)
            VoiceCommand.RATE_DOWN -> adjustRate(-0.2f)
        }
    }

    private fun adjustRate(delta: Float) {
        viewModelScope.launch {
            val current = prefsRepo.flow.first().ttsRate
            val next = (current + delta).coerceIn(0.5f, 2.0f)
            prefsRepo.setTtsRate(next)
            tts.setRate(next)
            tts.speak("Tốc độ đọc ${(next * 100).toInt()} phần trăm")
        }
    }

    private fun emit(e: NavEvent) {
        _navEvents.tryEmit(e)
    }

    override fun onCleared() {
        // Khong shutdown engine vi singleton — cho process die.
        super.onCleared()
    }

    private companion object {
        const val WELCOME_TEXT =
            "Chào mừng đến với Mắt AI. Chạm đôi vào nút Đọc tiền hoặc Đọc menu để bắt đầu. Lắc điện thoại để dừng đọc."
    }
}

sealed class NavEvent {
    data object GoHome : NavEvent()
    data object GoMoney : NavEvent()
    data object GoMenu : NavEvent()
    data object GoHistory : NavEvent()
    data object GoSettings : NavEvent()
    data object Repeat : NavEvent()
}
