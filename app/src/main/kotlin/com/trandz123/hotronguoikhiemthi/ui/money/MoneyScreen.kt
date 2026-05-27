package com.trandz123.hotronguoikhiemthi.ui.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trandz123.hotronguoikhiemthi.ui.common.PlaceholderScaffold

/**
 * Tab "Doc tien". Sap toi (tuan 5):
 *  - CameraX preview full man
 *  - ImageAnalysis use case: phat hien sang/ro/co vat the chu nhat → auto-capture
 *  - TFLite Interpreter (vnd_classifier.tflite) → label
 *  - NumberToVietnamese → TtsManager.speak("Hai tram nghin dong")
 */
@Composable
fun MoneyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScaffold(
        title = "Đọc tiền",
        hint = "Chức năng nhận diện mệnh giá VND sẽ có ở tuần 5 (sau khi train xong model).",
        onBack = onBack,
        modifier = modifier,
    )
}
