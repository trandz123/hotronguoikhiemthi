package com.trandz123.hotronguoikhiemthi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.trandz123.hotronguoikhiemthi.ui.nav.AppNavHost
import com.trandz123.hotronguoikhiemthi.ui.nav.NavEventBus
import com.trandz123.hotronguoikhiemthi.ui.theme.HoTroTheme
import com.trandz123.hotronguoikhiemthi.voice.ShakeDetector
import com.trandz123.hotronguoikhiemthi.voice.VoiceCommandService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var voiceService: VoiceCommandService
    private val viewModel: MainViewModel by viewModels()
    private lateinit var shake: ShakeDetector

    // Volume-up long-press detection
    private var volumeUpDownAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shake = ShakeDetector(this) { viewModel.onShakeStopTts() }

        setContent {
            HoTroTheme {
                AppRoot()
            }
        }
        // Wire voice → NavEvent
        lifecycleScope.launch {
            viewModel.navEvents.collect { NavEventBus.emit(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        shake.start()
        viewModel.playWelcomeOnce()
    }

    override fun onPause() {
        super.onPause()
        shake.stop()
        voiceService.stop()
    }

    override fun onDestroy() {
        voiceService.destroy()
        super.onDestroy()
    }

    /**
     * Volume-up giu 1.5s → trigger voice command. Khong consume event → he thong van chinh
     * volume binh thuong (am luong se thay doi truoc khi voice command bat — chap nhan).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && event?.repeatCount == 0) {
            volumeUpDownAt = System.currentTimeMillis()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && volumeUpDownAt > 0) {
            val heldMs = System.currentTimeMillis() - volumeUpDownAt
            volumeUpDownAt = 0L
            if (heldMs >= VOICE_HOLD_MS) {
                triggerVoiceCommand()
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun triggerVoiceCommand() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        voiceService.listenOnce(
            context = this,
            onCommand = { cmd -> viewModel.onVoiceCommand(cmd) },
            onError = { /* TTS engine inside VoiceCommandService could be triggered; skip */ },
        )
    }

    private companion object {
        const val VOICE_HOLD_MS = 1_500L
        const val REQ_AUDIO = 42
    }
}

@Composable
private fun AppRoot() {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        AppNavHost(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}
