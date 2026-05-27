package com.trandz123.hotronguoikhiemthi.ui.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trandz123.hotronguoikhiemthi.ui.common.PlaceholderScaffold

/**
 * Tab "Doc menu". Sap toi (tuan 6):
 *  - CameraX preview + ML Kit Text Recognition v2 (Latin script, ho tro tieng Viet)
 *  - Parser gom block theo y-coordinate, regex giá
 *  - 2 mode: doc toan bo / vuot tung mon
 */
@Composable
fun MenuScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScaffold(
        title = "Đọc menu",
        hint = "Chức năng đọc menu OCR sẽ có ở tuần 6.",
        onBack = onBack,
        modifier = modifier,
    )
}
