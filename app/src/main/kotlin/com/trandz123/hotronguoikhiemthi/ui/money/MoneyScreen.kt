package com.trandz123.hotronguoikhiemthi.ui.money

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.gestures.detectTapGestures
import com.trandz123.hotronguoikhiemthi.ml.MoneyResult
import com.trandz123.hotronguoikhiemthi.ui.camera.CameraPreviewView
import com.trandz123.hotronguoikhiemthi.ui.camera.FrameQualityAnalyzer
import com.trandz123.hotronguoikhiemthi.ui.camera.captureBitmap

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
    }

    if (!hasCameraPermission) {
        PermissionDeniedView(
            title = "Cần quyền camera",
            message = "Vui lòng cấp quyền camera để nhận diện tiền",
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    MoneyContent(
        viewModel = viewModel,
        onBack = onBack,
        onSwitchMode = onSwitchMode,
        modifier = modifier,
    )
}

@Composable
private fun MoneyContent(
    viewModel: MoneyViewModel,
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    var imageCapture by remember {
        mutableStateOf<androidx.camera.core.ImageCapture?>(null)
    }
    var autoCaptureEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        autoCaptureEnabled = viewModel.resolveAutoCapture()
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
                .pointerInput(Unit) {
                    // Swipe gesture: doi mode hoac thoat. Threshold 100px de tranh noise.
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
                                    if (dy < 0) onSwitchMode()  // vuot len → menu
                                    else onBack()                // vuot xuong → home
                                }
                                else -> Unit  // ignore swipe ngang
                            }
                        },
                        onDrag = { _, drag -> dx += drag.x; dy += drag.y },
                    )
                },
            analyzer = analyzer,
            onCameraReady = { ic -> imageCapture = ic },
        )

        // Overlay status: bottom area, semi-transparent dark cho do contrast
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
                text = "Đọc tiền",
                fontSize = 28.sp,
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )

            Text(
                text = statusText(state, autoCaptureEnabled),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )

            when (val s = state) {
                is MoneyUiState.Result -> {
                    Button(
                        onClick = { viewModel.scanAgain() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .semantics { contentDescription = "Quét tờ tiền tiếp theo" },
                    ) {
                        Text("Quét tiếp", fontSize = 22.sp)
                    }
                    Button(
                        onClick = { viewModel.repeatLast() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .semantics { contentDescription = "Đọc lại kết quả vừa rồi" },
                    ) {
                        Text("Đọc lại", fontSize = 22.sp)
                    }
                    Unit
                }
                else -> Unit
            }

            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .semantics { contentDescription = "Quay lại trang chính" },
            ) {
                Text("← Quay lại", fontSize = 20.sp)
            }
        }
    }
}

private fun statusText(state: MoneyUiState, autoCapture: Boolean): String = when (state) {
    is MoneyUiState.Analyzing -> if (autoCapture) {
        "Đang tìm tờ tiền — đưa camera lại gần"
    } else {
        "Chạm đôi để chụp"
    }
    MoneyUiState.Capturing -> "Đang chụp..."
    MoneyUiState.Classifying -> "Đang nhận diện..."
    is MoneyUiState.Result -> state.spokenText
}

@Composable
private fun PermissionDeniedView(
    title: String,
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            message,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
        ) {
            Text("Quay lại", fontSize = 22.sp)
        }
    }
}
