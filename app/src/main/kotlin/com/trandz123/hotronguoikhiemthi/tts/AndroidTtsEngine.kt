package com.trandz123.hotronguoikhiemthi.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * TTS engine offline dung [TextToSpeech] cua Android.
 * Mac dinh locale `vi-VN`. Neu device khong co data tieng Viet, se fallback en-US
 * (TalkBack van doc duoc, chi giong ko tu nhien).
 *
 * Khong dung [TextToSpeech.OnInitListener] callback truc tiep — boc qua coroutine:
 *  - constructor goi `TextToSpeech(ctx, listener)` -> dat state = Initializing
 *  - onInit() callback -> set Vietnamese, state = Ready (hoac Error neu fail)
 *
 * Multi-call ATTENTION: TextToSpeech.speak() voi QUEUE_FLUSH se cancel utterance
 * truoc do. Map continuation theo utterance ID de resume dung continuation.
 */
class AndroidTtsEngine(context: Context) : TtsManager {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(TtsState.Initializing)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    /** Map id utterance → continuation cua suspend speak(). */
    private val pending = ConcurrentHashMap<String, Continuation<Unit>>()

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var rate: Float = DEFAULT_RATE

    init {
        // Uu tien ep engine Google TTS (com.google.android.tts) — giong tieng Viet tu nhien.
        // Neu device khong co Google TTS, constructor fallback engine mac dinh he thong.
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureEngine()
            } else {
                Log.w(TAG, "Google TTS init failed status=$status, fallback default engine")
                tts?.shutdown()
                tts = TextToSpeech(appContext) { fallbackStatus ->
                    if (fallbackStatus == TextToSpeech.SUCCESS) {
                        configureEngine()
                    } else {
                        Log.e(TAG, "Default TTS engine also failed status=$fallbackStatus")
                        _state.value = TtsState.Error
                    }
                }
            }
        }
        tts = TextToSpeech(appContext, listener, GOOGLE_TTS_PACKAGE)
    }

    /** Cau hinh chung sau khi engine init: locale vi-VN, rate 0.9, pitch 1.0. */
    private fun configureEngine() {
        val engine = tts ?: return
        val result = engine.setLanguage(Locale("vi", "VN"))
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.w(TAG, "Vietnamese TTS not available, fallback to default locale")
            engine.setLanguage(Locale.getDefault())
        }
        engine.setSpeechRate(rate)
        engine.setPitch(DEFAULT_PITCH)
        engine.setOnUtteranceProgressListener(progressListener)
        _state.value = TtsState.Ready
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            _state.value = TtsState.Speaking
        }

        override fun onDone(utteranceId: String) {
            _state.value = TtsState.Ready
            pending.remove(utteranceId)?.resume(Unit)
        }

        @Deprecated("Deprecated in API 21+, kept for compat")
        override fun onError(utteranceId: String) {
            onError(utteranceId, TextToSpeech.ERROR)
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            Log.w(TAG, "TTS error utterance=$utteranceId code=$errorCode")
            _state.value = TtsState.Ready
            // Khong throw — accessibility app khong nen crash chi vi TTS fail.
            // Caller co the check state == Error de fallback engine khac.
            pending.remove(utteranceId)?.resume(Unit)
        }

        override fun onStop(utteranceId: String, interrupted: Boolean) {
            _state.value = TtsState.Ready
            pending.remove(utteranceId)?.resume(Unit)
        }
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts ?: run {
            Log.w(TAG, "speak() called but TTS not initialized")
            return
        }
        if (_state.value == TtsState.Initializing || _state.value == TtsState.Error) {
            // Bo qua thay vi block forever. Caller co the observe state truoc khi goi.
            Log.w(TAG, "speak() ignored, state=${_state.value}")
            return
        }

        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine<Unit> { cont ->
            pending[id] = cont
            cont.invokeOnCancellation {
                pending.remove(id)
                engine.stop()
            }
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (result == TextToSpeech.ERROR) {
                pending.remove(id)
                cont.resume(Unit)
            }
        }
    }

    override fun stop() {
        tts?.stop()
        val snapshot = pending.values.toList()
        pending.clear()
        snapshot.forEach { it.resume(Unit) }
        if (_state.value == TtsState.Speaking) {
            _state.value = TtsState.Ready
        }
    }

    override fun setRate(rate: Float) {
        require(rate in 0.5f..2.0f) { "Rate must be 0.5..2.0, got $rate" }
        this.rate = rate
        tts?.setSpeechRate(rate)
    }

    override fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private companion object {
        const val TAG = "AndroidTtsEngine"
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
        const val DEFAULT_RATE = 0.9f
        const val DEFAULT_PITCH = 1.0f
    }
}
