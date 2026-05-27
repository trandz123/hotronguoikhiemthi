package com.trandz123.hotronguoikhiemthi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trandz123.hotronguoikhiemthi.ui.common.PlaceholderScaffold

/**
 * Tab "Cai dat". Sap toi (tuan 7):
 *  - Toc do TTS (0.5x..2x)
 *  - Chon giong: FPT Nu/Nam Bac/Nam, Android default
 *  - Bat/tat auto-capture, high-contrast, rung
 *  - Persist bang DataStore
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScaffold(
        title = "Cài đặt",
        hint = "Tùy chỉnh tốc độ đọc, giọng, độ tương phản sẽ có ở tuần 7.",
        onBack = onBack,
        modifier = modifier,
    )
}
