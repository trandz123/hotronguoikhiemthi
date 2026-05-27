package com.trandz123.hotronguoikhiemthi.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trandz123.hotronguoikhiemthi.data.settings.ContrastMode
import com.trandz123.hotronguoikhiemthi.data.settings.TtsVoice

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsState()
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "Cài đặt",
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        // ----- Toc do TTS -----
        Section(title = "Tốc độ đọc: ${(prefs.ttsRate * 100).toInt()}%") {
            Slider(
                value = prefs.ttsRate,
                onValueChange = { /* dragging — preview only */ },
                onValueChangeFinished = { /* finalize at slider position */ },
                valueRange = 0.5f..2.0f,
                steps = 14, // bước 0.1
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Tốc độ đọc, hiện tại ${(prefs.ttsRate * 100).toInt()} phần trăm" },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { viewModel.setRate((prefs.ttsRate - 0.1f).coerceAtLeast(0.5f)) },
                    modifier = Modifier.weight(1f).height(64.dp),
                ) { Text("− Chậm hơn", fontSize = 18.sp) }
                Button(
                    onClick = { viewModel.setRate(1.0f) },
                    modifier = Modifier.weight(1f).height(64.dp),
                ) { Text("Reset", fontSize = 18.sp) }
                Button(
                    onClick = { viewModel.setRate((prefs.ttsRate + 0.1f).coerceAtMost(2.0f)) },
                    modifier = Modifier.weight(1f).height(64.dp),
                ) { Text("+ Nhanh hơn", fontSize = 18.sp) }
            }
        }

        // ----- Giong noi -----
        Section(title = "Giọng đọc") {
            TtsVoice.entries.forEach { voice ->
                val selected = prefs.ttsVoice == voice
                Button(
                    onClick = { viewModel.setVoice(voice) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .semantics {
                            contentDescription = "${voice.displayName}${if (selected) ", đang chọn" else ""}"
                        },
                ) {
                    Text(
                        text = (if (selected) "● " else "○ ") + voice.displayName,
                        fontSize = 18.sp,
                    )
                }
            }
        }

        // ----- Tuong phan -----
        Section(title = "Độ tương phản") {
            ContrastMode.entries.forEach { mode ->
                val selected = prefs.contrastMode == mode
                Button(
                    onClick = { viewModel.setContrast(mode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .semantics {
                            contentDescription = "${mode.displayName}${if (selected) ", đang chọn" else ""}"
                        },
                ) {
                    Text(
                        text = (if (selected) "● " else "○ ") + mode.displayName,
                        fontSize = 18.sp,
                    )
                }
            }
        }

        // ----- Toggles -----
        ToggleRow(
            label = "Rung phản hồi",
            checked = prefs.vibrationEnabled,
            onCheckedChange = { viewModel.setVibration(it) },
        )
        ToggleRow(
            label = "Tự động chụp tiền",
            checked = prefs.autoCaptureEnabled,
            onCheckedChange = { viewModel.setAutoCapture(it) },
        )
        ToggleRow(
            label = "Điều khiển bằng giọng nói (giữ phím tăng âm lượng 1,5 giây)",
            checked = prefs.voiceCommandEnabled,
            onCheckedChange = { viewModel.setVoiceCommand(it) },
        )

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .semantics { contentDescription = "Quay lại trang chính" },
        ) { Text("← Quay lại", fontSize = 22.sp) }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics {
                contentDescription = "$label, ${if (checked) "đang bật" else "đang tắt"}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
