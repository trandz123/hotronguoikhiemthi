package com.trandz123.hotronguoikhiemthi.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrap [SpeechRecognizer]. Goi [listenOnce] de bat dau nghe 1 lan, ket qua tra ve qua callback.
 *
 * Lifecycle:
 *  - [SpeechRecognizer] phai duoc tao tren main thread.
 *  - Recreate moi lan listen de tranh state bi corrupt sau loi network.
 */
@Singleton
class VoiceCommandService @Inject constructor() {

    private var recognizer: SpeechRecognizer? = null

    /**
     * Listen 1 lan. [onCommand] nhan VoiceCommand (da map tu raw text).
     * Goi tren main thread.
     */
    fun listenOnce(context: Context, onCommand: (VoiceCommand) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Thiết bị không hỗ trợ nhận diện giọng nói")
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
                val raw = matches.firstOrNull().orEmpty().lowercase().trim()
                val cmd = VoiceCommand.parse(raw)
                if (cmd != null) onCommand(cmd) else onError("Không hiểu lệnh: $raw")
            }

            override fun onError(error: Int) {
                onError("Lỗi nhận diện (mã $error)")
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói lệnh của bạn")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        runCatching { r.startListening(intent) }
            .onFailure {
                Log.w(TAG, "startListening failed", it)
                onError("Không bật được nhận diện giọng nói")
            }
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private companion object {
        const val TAG = "VoiceCommandService"
    }
}

/**
 * Lenh giong noi nhan ra duoc. [parse] map raw text → command.
 * Tat ca matcher la "chua" (contains) de robust voi tieng noi tu nhien.
 */
enum class VoiceCommand(private vararg val keywords: String) {
    READ_MONEY("đọc tiền", "doc tien", "tiền"),
    READ_MENU("đọc menu", "doc menu", "menu", "thực đơn"),
    REPEAT("đọc lại", "doc lai", "nhắc lại", "lặp lại"),
    STOP("dừng", "dung", "stop", "tạm dừng"),
    RATE_UP("nhanh hơn", "đọc nhanh", "to hơn"),
    RATE_DOWN("chậm hơn", "đọc chậm", "nhỏ hơn"),
    EXIT("thoát", "trang chính", "về"),
    HISTORY("lịch sử", "lich su"),
    SETTINGS("cài đặt", "cai dat", "thiết lập", "tùy chỉnh");

    companion object {
        fun parse(text: String): VoiceCommand? {
            val normalized = text.trim().lowercase()
            return entries.firstOrNull { cmd -> cmd.keywords.any { normalized.contains(it) } }
        }
    }
}

