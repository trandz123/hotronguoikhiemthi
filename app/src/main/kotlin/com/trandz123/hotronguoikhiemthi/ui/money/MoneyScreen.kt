package com.trandz123.hotronguoikhiemthi.ui.money

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
import com.trandz123.hotronguoikhiemthi.ml.MoneyResult
import com.trandz123.hotronguoikhiemthi.ui.camera.CameraPreviewView
import com.trandz123.hotronguoikhiemthi.ui.camera.FrameQualityAnalyzer
import com.trandz123.hotronguoikhiemthi.ui.camera.captureBitmap
import com.trandz123.hotronguoikhiemthi.util.hapticStrong
import com.trandz123.hotronguoikhiemthi.util.hapticTick

@Composable
fun MoneyScreen(
    onBack: () -> Unit,
    onSwitchMode: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MoneyViewModel = hiltViewModel(),
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
                "Cần quyền camera để nhận diện tiền",
                fontSize = 28.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
        }
        return
    }

    MoneyContent(
        viewModel = viewModel,
        onSwitchMode = onSwitchMode,
        modifier = modifier,
    )
}

@Composable
private fun MoneyContent(
    viewModel: MoneyViewModel,
    onSwitchMode: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var imageCapture by remember {
        mutableStateOf<androidx.camera.core.ImageCapture?>(null)
    }
    var autoCaptureEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        autoCaptureEnabled = viewModel.resolveAutoCapture()
    }

    // Rung manh khi nhan dien duoc tien.
    LaunchedEffect(state) {
        val s = state
        if (s is MoneyUiState.Result && s.moneyResult is MoneyResult.Recognized) {
            hapticStrong(context)
        }
    }

    val analyzer = remember(autoCaptureEnabled) {
        FrameQualityAnalyzer(
            onResult = { q -> viewModel.onFrameQuality(q.brightness, q.sharpness) },
            onStable = {
                if (autoCaptureEnabled && state is MoneyUiState.Analyzing) {
                    val ic = imageCapture ?: return@FrameQualityAnalyzer
                    viewModel.onCapture { ic.captureBitmap(context) }
                }
            },
        )
    }

    val swipeUpToMenu: () -> Unit = remember(onSwitchMode) {
        {
            hapticTick(context)
            onSwitchMode()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CameraPreviewView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        val ic = imageCapture ?: return@detectTapGestures
                        viewModel.onCapture { ic.captureBitmap(context) }
                    })
                }
                .pointerInput(swipeUpToMenu) {
                    // Theo spec UX:
                    //  - Vuot len   (dy < 0) → ve menu
                    //  - Vuot xuong (dy > 0) → reset quet lai
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
                                    if (dy < 0) swipeUpToMenu()
                                    else viewModel.scanAgain()
                                }
                                else -> Unit
                            }
                        },
                        onDrag = { _, drag -> dx += drag.x; dy += drag.y },
                    )
                },
            analyzer = analyzer,
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
                text = "Chế độ nhận diện tiền",
                fontSize = 28.sp,
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = statusText(state),
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                "Vuốt lên để chuyển sang đọc menu. Vuốt xuống để quét lại.",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun statusText(state: MoneyUiState): String = when (state) {
    is MoneyUiState.Analyzing -> "Đang tìm tờ tiền..."
    MoneyUiState.Capturing -> "Đang chụp..."
    MoneyUiState.Classifying -> "Đang nhận diện..."
    is MoneyUiState.Result -> state.spokenText
}
