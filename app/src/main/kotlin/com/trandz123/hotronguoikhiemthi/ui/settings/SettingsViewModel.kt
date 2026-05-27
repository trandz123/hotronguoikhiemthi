package com.trandz123.hotronguoikhiemthi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trandz123.hotronguoikhiemthi.data.settings.ContrastMode
import com.trandz123.hotronguoikhiemthi.data.settings.PreferencesRepository
import com.trandz123.hotronguoikhiemthi.data.settings.TtsVoice
import com.trandz123.hotronguoikhiemthi.data.settings.UserPreferences
import com.trandz123.hotronguoikhiemthi.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: PreferencesRepository,
    private val tts: TtsManager,
) : ViewModel() {

    val prefs: StateFlow<UserPreferences> = repo.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setRate(rate: Float) {
        viewModelScope.launch {
            repo.setTtsRate(rate)
            tts.setRate(rate)
            tts.speak("Tốc độ ${(rate * 100).toInt()} phần trăm")
        }
    }

    fun setVoice(voice: TtsVoice) {
        viewModelScope.launch {
            repo.setTtsVoice(voice)
            tts.speak("Đã chọn giọng ${voice.displayName}")
        }
    }

    fun setContrast(mode: ContrastMode) {
        viewModelScope.launch {
            repo.setContrastMode(mode)
            tts.speak("Độ tương phản: ${mode.displayName}")
        }
    }

    fun setVibration(enabled: Boolean) {
        viewModelScope.launch {
            repo.setVibration(enabled)
            tts.speak(if (enabled) "Đã bật rung" else "Đã tắt rung")
        }
    }

    fun setAutoCapture(enabled: Boolean) {
        viewModelScope.launch {
            repo.setAutoCapture(enabled)
            tts.speak(if (enabled) "Đã bật tự động chụp" else "Đã tắt tự động chụp")
        }
    }

    fun setVoiceCommand(enabled: Boolean) {
        viewModelScope.launch {
            repo.setVoiceCommand(enabled)
            tts.speak(if (enabled) "Đã bật điều khiển bằng giọng nói" else "Đã tắt điều khiển bằng giọng nói")
        }
    }
}
