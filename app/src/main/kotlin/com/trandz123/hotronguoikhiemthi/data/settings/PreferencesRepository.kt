package com.trandz123.hotronguoikhiemthi.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val flow: Flow<UserPreferences> = context.dataStore.data.map { p ->
        UserPreferences(
            ttsRate = p[Keys.TTS_RATE] ?: 1.0f,
            ttsVoice = (p[Keys.TTS_VOICE]?.let { runCatching { TtsVoice.valueOf(it) }.getOrNull() })
                ?: TtsVoice.AUTO,
            contrastMode = (p[Keys.CONTRAST]?.let { runCatching { ContrastMode.valueOf(it) }.getOrNull() })
                ?: ContrastMode.SYSTEM,
            vibrationEnabled = p[Keys.VIBRATION] ?: true,
            autoCaptureEnabled = p[Keys.AUTO_CAPTURE] ?: false,
            voiceCommandEnabled = p[Keys.VOICE_COMMAND] ?: true,
        )
    }

    suspend fun setTtsRate(rate: Float) = context.dataStore.edit {
        it[Keys.TTS_RATE] = rate.coerceIn(0.5f, 2.0f)
    }

    suspend fun setTtsVoice(voice: TtsVoice) = context.dataStore.edit {
        it[Keys.TTS_VOICE] = voice.name
    }

    suspend fun setContrastMode(mode: ContrastMode) = context.dataStore.edit {
        it[Keys.CONTRAST] = mode.name
    }

    suspend fun setVibration(enabled: Boolean) = context.dataStore.edit {
        it[Keys.VIBRATION] = enabled
    }

    suspend fun setAutoCapture(enabled: Boolean) = context.dataStore.edit {
        it[Keys.AUTO_CAPTURE] = enabled
    }

    suspend fun setVoiceCommand(enabled: Boolean) = context.dataStore.edit {
        it[Keys.VOICE_COMMAND] = enabled
    }

    private object Keys {
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val CONTRAST = stringPreferencesKey("contrast")
        val VIBRATION = booleanPreferencesKey("vibration")
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val VOICE_COMMAND = booleanPreferencesKey("voice_command")
    }
}
