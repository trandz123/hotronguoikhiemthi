package com.trandz123.hotronguoikhiemthi.ui.menu

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.trandz123.hotronguoikhiemthi.ui.camera.CameraPreviewView
import com.trandz123.hotronguoikhiemthi.ui.camera.captureBitmap
import com.trandz123.hotronguoikhiemthi.util.hapticTick

@Composable
fun MenuScreen(
    onBack: () -> Unit,
    onSwitchMode: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MenuViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permLauncher.launch(Manifest.permission.CAMERA)
        viewModel.playWelcomeOnce()
    }

    if (!hasCameraPermission) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Cần quyền camera để đọc menu",
                fontSize = 28.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
        }
        return
    }

    MenuContent(viewModel, onBack, onSwitchMode, modifier)
}

@Composable
private fun MenuContent(
    viewModel: MenuViewModel,
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var imageCapture by remember {
        mutableStateOf<androidx.camera.core.ImageCapture?>(null)
    }

    // Trigger countdown khi camera san sang va state == Idle.
    LaunchedEffect(state, imageCapture) {
        if (state is MenuUiState.Idle && imageCapture != null) {
            val ic = imageCapture!!
            viewModel.startCountdown { ic.captureBitmap(context) }
        }
    }

    // Tu dong scan lai sau khi error message duoc phat — cho user co the vuot len.
    // Khong auto reset; user vuot len -> scanAgain().

    val swipeUpToMoney: () -> Unit = remember(onSwitchMode, viewModel) {
        {
            hapticTick(context)
            viewModel.switchToMoney()
            onSwitchMode()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Camera + status overlay luon hien khi chua co Loaded (hoac dang Reading).
        when (state) {
            is MenuUiState.Loaded -> {
                LoadedView(
                    state = state,
                    onSwipeNext = { hapticTick(context); viewModel.nextItem() },
                    onSwipePrev = { hapticTick(context); viewModel.prevItem() },
                    onSwipeUp = { swipeUpToMoney() },
                    onSwipeDown = { hapticTick(context); viewModel.selectCurrent() },
                    onDoubleTap = { hapticTick(context); viewModel.readSelected() },
                    onLongPress = { hapticTick(context); viewModel.scanAgain() },
                )
            }
            else -> {
                CameraPreviewView(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                // Double tap = scan lai menu thu cong
                                hapticTick(context)
                                viewModel.scanAgain()
                            })
                        }
                        .pointerInput(swipeUpToMoney) {
                            // UX dong nhat: vuot LEN = chuyen sang doc tien.
                            var dx = 0f
                            var dy = 0f
                            detectDragGestures(
                                onDragStart = { dx = 0f; dy = 0f },
                                onDragEnd = {
                                    val absX = kotlin.math.abs(dx)
                                    val absY = kotlin.math.abs(dy)
                                    if (absY > absX && absY > 100f && dy < 0) swipeUpToMoney()
                                },
                                onDrag = { _, drag -> dx += drag.x; dy += drag.y },
                            )
                        },
                    onCameraReady = { ic -> imageCapture = ic },
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Chế độ đọc menu",
                        fontSize = 28.sp,
                        color = Color.White,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        statusText(state),
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Vuốt lên để chuyển sang đọc tiền.",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedView(
    state: MenuUiState,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val loaded = state as? MenuUiState.Loaded ?: return
    val items = loaded.items
    val currentIndex = loaded.currentIndex
    val current = items.getOrNull(currentIndex)
    val isSelected = current?.let { c ->
        loaded.selectedItems.any { it.name == c.name && it.priceVnd == c.priceVnd }
    } ?: false
    val selectedCount = loaded.selectedItems.size
    val selectedTotal = loaded.selectedItems.mapNotNull { it.priceVnd }.sum()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() },
                )
            }
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        val absX = kotlin.math.abs(dx)
                        val absY = kotlin.math.abs(dy)
                        when {
                            absX < 80f && absY < 80f -> Unit
                            absX > absY -> if (dx > 0) onSwipeNext() else onSwipePrev()
                            else -> if (dy < 0) onSwipeUp() else onSwipeDown()
                        }
                    },
                    onDrag = { _, drag -> dx += drag.x; dy += drag.y },
                )
            }
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Đọc menu",
            fontSize = 32.sp,
            color = Color.White,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Món ${currentIndex + 1}/${items.size}" + if (isSelected) " ✓ đã chọn" else "",
            fontSize = 22.sp,
            color = if (isSelected) Color.Green else Color.White.copy(alpha = 0.8f),
        )
        Text(
            current?.let { it.name + (it.priceVnd?.let { p -> " — ${formatVnd(p)} đồng" } ?: "") } ?: "",
            fontSize = 32.sp,
            color = Color.Yellow,
            textAlign = TextAlign.Center,
        )
        if (selectedCount > 0) {
            Text(
                "Đã chọn: $selectedCount món" +
                    if (selectedTotal > 0) " — ${formatVnd(selectedTotal)} đồng" else "",
                fontSize = 18.sp,
                color = Color.Green,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            "Vuốt phải/trái: món tiếp/trước. Vuốt xuống: chọn món. " +
                "Chạm đôi: nghe danh sách đã chọn. Giữ lâu: quét lại menu. Vuốt lên: đọc tiền.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun statusText(state: MenuUiState): String = when (state) {
    MenuUiState.Idle -> "Sẵn sàng..."
    is MenuUiState.CountingDown -> "Lấy nét... ${state.secondsLeft.coerceAtLeast(0)}"
    MenuUiState.Capturing -> "Đang chụp..."
    MenuUiState.OcrProcessing -> "Đang đọc chữ..."
    MenuUiState.GeminiParsing -> "Đang ghép món..."
    is MenuUiState.Loaded -> "Đã có ${state.items.size} món"
    is MenuUiState.Reading -> "Đang đọc..."
    is MenuUiState.Error -> "Lỗi: ${state.message}"
}

private fun formatVnd(amount: Long): String {
    val s = amount.toString().reversed().chunked(3).joinToString(".").reversed()
    return s
}
