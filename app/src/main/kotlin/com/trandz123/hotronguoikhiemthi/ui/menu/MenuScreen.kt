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

    val swipeDownToMoney: () -> Unit = remember(onSwitchMode) {
        {
            hapticTick(context)
            onSwitchMode()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Camera + status overlay luon hien khi chua co Loaded (hoac dang Reading).
        when (state) {
            is MenuUiState.Loaded -> {
                LoadedView(
                    state = state,
                    onSwipeNext = { viewModel.nextItem() },
                    onSwipePrev = { viewModel.prevItem() },
                    onSwipeUp = { viewModel.scanAgain() }, // re-scan menu
                    onSwipeDown = { swipeDownToMoney() }, // sang tien
                    onDoubleTap = { viewModel.readAll() },
                )
            }
            else -> {
                CameraPreviewView(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                val ic = imageCapture ?: return@detectTapGestures
                                viewModel.onCapture { ic.captureBitmap(context) }
                            })
                        }
                        .pointerInput(swipeDownToMoney) {
                            // Theo spec UX:
                            //  - Vuot xuong (dy > 0) → chuyen sang doc tien
                            //  - Vuot len   (dy < 0) → chup lai menu (scanAgain → Idle → countdown)
                            var dx = 0f
                            var dy = 0f
                            detectDragGestures(
                                onDragStart = { dx = 0f; dy = 0f },
                                onDragEnd = {
                                    val absX = kotlin.math.abs(dx)
                                    val absY = kotlin.math.abs(dy)
                                    when {
                                        absX < 100f && absY < 100f -> Unit
                                        absY > absX -> {
                                            if (dy > 0) swipeDownToMoney()
                                            else viewModel.scanAgain()
                                        }
                                        else -> Unit
                                    }
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
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
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
                        "Vuốt xuống để chuyển sang đọc tiền. Vuốt lên để chụp lại.",
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
) {
    val loaded = state as? MenuUiState.Loaded ?: return
    val items = loaded.items
    val currentIndex = loaded.currentIndex
    val current = items.getOrNull(currentIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
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
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Đọc menu",
            fontSize = 32.sp,
            color = Color.White,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Món ${currentIndex + 1}/${items.size}",
            fontSize = 22.sp,
            color = Color.White.copy(alpha = 0.8f),
        )
        Text(
            current?.let { it.name + (it.priceVnd?.let { p -> " — ${formatVnd(p)} đồng" } ?: "") } ?: "",
            fontSize = 32.sp,
            color = Color.Yellow,
            textAlign = TextAlign.Center,
        )
        Text(
            "Vuốt phải sang món tiếp, vuốt trái lùi, vuốt lên chụp lại menu, vuốt xuống sang đọc tiền.",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun statusText(state: MenuUiState): String = when (state) {
    MenuUiState.Idle -> "Sẵn sàng..."
    is MenuUiState.CountingDown -> "Lấy nét... ${state.secondsLeft.coerceAtLeast(0)}"
    MenuUiState.Capturing -> "Đang chụp..."
    MenuUiState.Processing -> "Đang phân tích menu..."
    is MenuUiState.Loaded -> "Đã có ${state.items.size} món"
    is MenuUiState.Reading -> "Đang đọc..."
    is MenuUiState.Error -> "Lỗi: ${state.message}"
}

private fun formatVnd(amount: Long): String {
    val s = amount.toString().reversed().chunked(3).joinToString(".").reversed()
    return s
}
