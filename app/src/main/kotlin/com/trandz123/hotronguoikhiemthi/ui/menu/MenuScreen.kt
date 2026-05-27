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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
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

@Composable
fun MenuScreen(
    onBack: () -> Unit,
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
    }

    if (!hasCameraPermission) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Cần quyền camera",
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Vui lòng cấp quyền camera để đọc menu",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            ) { Text("Quay lại", fontSize = 22.sp) }
        }
        return
    }

    MenuContent(viewModel, onBack, modifier)
}

@Composable
private fun MenuContent(
    viewModel: MenuViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var imageCapture by remember {
        mutableStateOf<androidx.camera.core.ImageCapture?>(null)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Khi chua co Loaded → hien camera. Loaded → hien danh sach va dieu khien swipe.
        when (state) {
            is MenuUiState.Loaded, is MenuUiState.Reading -> {
                LoadedView(
                    state = state,
                    onSwipeNext = { viewModel.nextItem() },
                    onSwipePrev = { viewModel.prevItem() },
                    onSwipeUp = { viewModel.repeatCurrent() },
                    onSwipeDown = { viewModel.stopReading() },
                    onDoubleTap = { viewModel.readAll() },
                    onScanAgain = { viewModel.scanAgain() },
                    onBack = onBack,
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Đọc menu",
                        fontSize = 28.sp,
                        color = Color.White,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        statusText(state),
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .semantics { contentDescription = "Quay lại trang chính" },
                    ) { Text("← Quay lại", fontSize = 20.sp) }
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
    onScanAgain: () -> Unit,
    onBack: () -> Unit,
) {
    val items = when (state) {
        is MenuUiState.Loaded -> state.items
        is MenuUiState.Reading -> state.items
        else -> emptyList()
    }
    val currentIndex = when (state) {
        is MenuUiState.Loaded -> state.currentIndex
        is MenuUiState.Reading -> state.currentIndex
        else -> 0
    }
    val current = items.getOrNull(currentIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            }
            .pointerInput(Unit) {
                // 1 drag detector chung: accumulate dx, dy → cuoi drag dieu huong theo
                // truc co bien thien lon nhat (tranh xung dot voi tap detector).
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        val absX = kotlin.math.abs(dx)
                        val absY = kotlin.math.abs(dy)
                        when {
                            absX < 80f && absY < 80f -> Unit  // mini drag → ignore
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
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Món ${currentIndex + 1}/${items.size}",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            current?.let { it.name + (it.priceVnd?.let { p -> " — ${formatVnd(p)} đồng" } ?: "") } ?: "",
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            "Vuốt phải → tiếp, ← lùi, ↑ đọc lại, ↓ dừng. Chạm đôi để đọc toàn bộ.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onScanAgain, modifier = Modifier.height(72.dp)) {
                Text("Quét lại", fontSize = 20.sp)
            }
            Button(onClick = onBack, modifier = Modifier.height(72.dp)) {
                Text("Quay lại", fontSize = 20.sp)
            }
        }
    }
}

private fun statusText(state: MenuUiState): String = when (state) {
    MenuUiState.Idle -> "Chạm đôi để chụp menu"
    MenuUiState.Capturing -> "Đang chụp..."
    MenuUiState.Processing -> "Đang phân tích menu..."
    is MenuUiState.Loaded -> "Đã có ${state.items.size} mục"
    is MenuUiState.Reading -> "Đang đọc..."
}

private fun formatVnd(amount: Long): String {
    val s = amount.toString().reversed().chunked(3).joinToString(".").reversed()
    return s
}
