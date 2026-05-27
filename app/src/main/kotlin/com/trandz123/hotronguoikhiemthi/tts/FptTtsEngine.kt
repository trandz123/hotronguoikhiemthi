package com.trandz123.hotronguoikhiemthi.tts

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * FPT.AI Text-to-Speech engine (cloud).
 *
 * Quy trinh:
 *  1. POST text + API key → endpoint v5/tts → response JSON co field `async` chua URL MP3
 *  2. Poll URL MP3 (server can vai giay de generate) → khi 200 OK, download
 *  3. Phat bang [MediaPlayer]
 *
 * Neu [apiKey] rong, [speak] tu im lang (caller dung [RoutedTtsEngine] de fallback).
 * Khong throw — accessibility app khong nen crash chi vi TTS that bai.
 *
 * Voice code chuyen qua [voiceCode]:
 *  - "banmai" - Nu Bac
 *  - "leminh" - Nam Bac
 *  - "linhsan" - Nu Nam
 *  - null → mac dinh server (banmai)
 */
class FptTtsEngine(
    context: Context,
    private val apiKey: String,
    private val voiceCode: String? = "banmai",
) : TtsManager {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(
        if (apiKey.isBlank()) TtsState.Error else TtsState.Ready
    )
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    @Volatile
    private var rate: Float = 1.0f

    @Volatile
    private var currentPlayer: MediaPlayer? = null

    /**
     * Tra ve true neu engine kha dung (co API key + state != Error). Caller co the
     * dung property nay de quyet fallback.
     */
    val isAvailable: Boolean get() = apiKey.isNotBlank() && _state.value != TtsState.Error

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        if (apiKey.isBlank()) {
            Log.w(TAG, "FPT API key empty, ignoring speak()")
            return
        }
        try {
            val mp3Url = withContext(Dispatchers.IO) { requestTts(text) }
            if (mp3Url == null) {
                Log.w(TAG, "FPT returned no MP3 URL")
                _state.value = TtsState.Error
                return
            }
            _state.value = TtsState.Speaking
            playUrl(mp3Url)
            _state.value = TtsState.Ready
        } catch (t: Throwable) {
            Log.w(TAG, "FPT TTS failed: ${t.message}")
            _state.value = TtsState.Error
        }
    }

    override fun stop() {
        currentPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        currentPlayer = null
        if (_state.value == TtsState.Speaking) _state.value = TtsState.Ready
    }

    override fun setRate(rate: Float) {
        require(rate in 0.5f..2.0f) { "Rate must be 0.5..2.0" }
        this.rate = rate
    }

    override fun shutdown() {
        stop()
    }

    /**
     * Goi v5/tts. Tra ve URL MP3 (sau khi poll thanh cong) hoac null.
     */
    private fun requestTts(text: String): String? {
        val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("api-key", apiKey)
            setRequestProperty("voice", voiceCode ?: "banmai")
            setRequestProperty("speed", encodeSpeed(rate))
            setRequestProperty("format", "mp3")
            doOutput = true
            connectTimeout = 5_000
            readTimeout = 10_000
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(text) }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "FPT request failed: HTTP $code")
                return null
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                .use { it.readText() }
            val json = JSONObject(body)
            val asyncUrl = json.optString("async").takeIf { it.isNotBlank() } ?: return null

            // Poll URL: FPT mat ~1-3s generate. Thu 6 lan, moi lan cach 1s.
            repeat(POLL_MAX_RETRIES) {
                Thread.sleep(POLL_INTERVAL_MS)
                val poll = URL(asyncUrl).openConnection() as HttpURLConnection
                poll.requestMethod = "HEAD"
                poll.connectTimeout = 3_000
                if (poll.responseCode in 200..299) {
                    poll.disconnect()
                    return asyncUrl
                }
                poll.disconnect()
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun playUrl(url: String) = suspendCancellableCoroutine<Unit> { cont ->
        val mp = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener { it.start() }
            setOnCompletionListener {
                it.release()
                if (cont.isActive) cont.resume(Unit)
            }
            setOnErrorListener { player, _, _ ->
                player.release()
                if (cont.isActive) cont.resume(Unit)
                true
            }
        }
        currentPlayer = mp
        cont.invokeOnCancellation {
            mp.runCatching { if (isPlaying) stop(); release() }
            currentPlayer = null
        }
        try {
            mp.prepareAsync()
        } catch (t: Throwable) {
            mp.release()
            if (cont.isActive) cont.resume(Unit)
        }
    }

    /**
     * FPT API `speed` chap nhan int trong khoang [-3..3], 0 la binh thuong.
     * Map rate [0.5..2.0] → [-3..3] tuyen tinh.
     */
    private fun encodeSpeed(rate: Float): String {
        val mapped = ((rate - 1.0f) * 4).toInt().coerceIn(-3, 3)
        return mapped.toString()
    }

    private companion object {
        const val TAG = "FptTtsEngine"
        const val API_URL = "https://api.fpt.ai/hmi/tts/v5"
        const val POLL_MAX_RETRIES = 6
        const val POLL_INTERVAL_MS = 1_000L
    }
}
