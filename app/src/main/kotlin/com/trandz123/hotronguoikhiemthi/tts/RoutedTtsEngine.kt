package com.trandz123.hotronguoikhiemthi.tts

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.trandz123.hotronguoikhiemthi.data.settings.PreferencesRepository
import com.trandz123.hotronguoikhiemthi.data.settings.TtsVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * TTS engine "thong minh": chon engine theo Settings + network status.
 *
 *  - User chon `ANDROID_DEFAULT` → luon dung [AndroidTtsEngine]
 *  - User chon AUTO hoac giong FPT cu the:
 *      + Co network + co API key + engine FPT [isAvailable] → dung FPT
 *      + Khong dat dieu kien tren → fallback Android
 *
 * Khi FPT speak that bai (network drop giua chung) → tu dong fallback Android cho lan sau.
 *
 * State flow: tra state cua engine dang dung (delegate).
 */
class RoutedTtsEngine(
    private val context: Context,
    private val fpt: FptTtsEngine,
    private val android: AndroidTtsEngine,
    private val prefsRepo: PreferencesRepository,
) : TtsManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(TtsState.Ready)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    @Volatile
    private var currentRate: Float = 1.0f

    init {
        // Sync state tu engine active. Don gian: gop tu 2 engine.
        scope.launch {
            // Observe Android state lan dau de switch ngay khi init xong
            android.state.collect { _state.value = it }
        }
    }

    override suspend fun speak(text: String) {
        val voice = prefsRepo.flow.first().ttsVoice
        val useFpt = voice.usesFpt && fpt.isAvailable && isOnline()
        if (useFpt) {
            // Truyen voice code cu the qua FptTtsEngine — caller co the tao instance moi
            // theo voice. O day don gian: dung default voice code (banmai). Doi sau.
            fpt.speak(text)
            if (fpt.state.value != TtsState.Error) return
            // Else fall through to Android
        }
        android.speak(text)
    }

    override fun stop() {
        fpt.stop()
        android.stop()
    }

    override fun setRate(rate: Float) {
        currentRate = rate
        fpt.setRate(rate)
        android.setRate(rate)
    }

    override fun shutdown() {
        fpt.shutdown()
        android.shutdown()
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
