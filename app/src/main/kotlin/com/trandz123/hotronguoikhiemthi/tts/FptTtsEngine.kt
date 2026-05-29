package com.trandz123.hotronguoikhiemthi.tts

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * FPT.AI Text-to-Speech engine voi cancellation TUYET DOI:
 *   - Speak() moi → cancel speak() cu NGAY LAP TUC + release MediaPlayer
 *   - Job-based: KHONG dung Mutex (Mutex chan ca 1-3s khi cho FPT response)
 *   - playUrl dung suspendCancellableCoroutine → invokeOnCancellation release MP3
 *   - Stop() de cancel everything immediately
 *
 * UX cho nguoi mu: swipe sang mon moi → cat tieng cu, play tieng moi ngay.
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

    @Volatile private var rate: Float = 1.0f
    @Volatile private var currentPlayer: MediaPlayer? = null
    @Volatile private var currentSpeakJob: Job? = null

    val isAvailable: Boolean get() = apiKey.isNotBlank()

    override suspend fun speak(text: String) {
        if (text.isBlank() || apiKey.isBlank()) return

        // INTERRUPT bat ki utterance dang chay (request hoac play)
        currentSpeakJob?.cancel()
        releasePlayer()

        coroutineScope {
            val job = launch { doSpeak(text) }
            currentSpeakJob = job
            try {
                job.join()
            } catch (e: CancellationException) {
                // Job bi cancel — do la behavior mong muon
            } finally {
                if (currentSpeakJob === job) currentSpeakJob = null
            }
        }
    }

    private suspend fun doSpeak(text: String) {
        if (_state.value == TtsState.Error) _state.value = TtsState.Ready
        try {
            val mp3Url = withContext(Dispatchers.IO) { requestTts(text) }
            // Sau khi tra ve tu IO blocking, check cancel
            coroutineContext.ensureActive()
            if (mp3Url == null) return
            _state.value = TtsState.Speaking
            playUrl(mp3Url)
            _state.value = TtsState.Ready
        } catch (e: CancellationException) {
            releasePlayer()
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "FPT TTS failed: ${t.message}")
            _state.value = TtsState.Ready
        }
    }

    override fun stop() {
        currentSpeakJob?.cancel()
        currentSpeakJob = null
        releasePlayer()
        if (_state.value == TtsState.Speaking) _state.value = TtsState.Ready
    }

    override fun setRate(rate: Float) {
        require(rate in 0.5f..2.0f) { "Rate must be 0.5..2.0" }
        this.rate = rate
    }

    override fun shutdown() = stop()

    private fun releasePlayer() {
        currentPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        currentPlayer = null
    }

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
                it.runCatching { release() }
                if (cont.isActive) cont.resume(Unit)
            }
            setOnErrorListener { player, _, _ ->
                player.runCatching { release() }
                if (cont.isActive) cont.resume(Unit)
                true
            }
        }
        currentPlayer = mp
        cont.invokeOnCancellation {
            mp.runCatching { if (isPlaying) stop(); release() }
            if (currentPlayer === mp) currentPlayer = null
        }
        try {
            mp.prepareAsync()
        } catch (t: Throwable) {
            mp.runCatching { release() }
            if (cont.isActive) cont.resume(Unit)
        }
    }

    private fun encodeSpeed(rate: Float): String {
        val mapped = ((rate - 1.0f) * 4).toInt().coerceIn(-3, 3)
        return mapped.toString()
    }

    private companion object {
        const val TAG = "FptTtsEngine"
        const val API_URL = "https://api.fpt.ai/hmi/tts/v5"
        // Poll nhanh: 100ms interval, max 3s (30 retries) -- giam delay tu 1-2s xuong 100-300ms
        const val POLL_MAX_RETRIES = 30
        const val POLL_INTERVAL_MS = 100L
    }
}
