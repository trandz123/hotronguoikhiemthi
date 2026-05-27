package com.trandz123.hotronguoikhiemthi.tts

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface chung cho 2 engine: [AndroidTtsEngine] (offline, mac dinh) va
 * `FptTtsEngine` (cloud, giong dep — them sau khi co API key).
 *
 * Toan bo API la suspend de coroutine-friendly:
 *  - [speak] tra ve khi TTS doc xong (hoac bi cancel)
 *  - [stop] cancel ngay lap tuc
 *
 * Implementation phai thread-safe: nhieu coroutine co the goi cung luc.
 */
interface TtsManager {

    val state: StateFlow<TtsState>

    /**
     * Doc 1 doan text. Suspend cho den khi:
     *  - TTS doc xong → return normally
     *  - User goi [stop] → return (khong throw)
     *  - Coroutine bi cancel → throw CancellationException
     */
    suspend fun speak(text: String)

    /** Dung ngay TTS dang phat. Idempotent. */
    fun stop()

    /**
     * @param rate 0.5f..2.0f. 1.0f = binh thuong.
     */
    fun setRate(rate: Float)

    /** Don tai nguyen. Goi khi process die. Idempotent. */
    fun shutdown()
}

enum class TtsState {
    /** Chua init xong (vd Android TTS dang load engine). */
    Initializing,

    /** San sang nhan speak(). */
    Ready,

    /** Dang phat. */
    Speaking,

    /** Init that bai (vd thiet bi khong co engine TTS). */
    Error,
}
