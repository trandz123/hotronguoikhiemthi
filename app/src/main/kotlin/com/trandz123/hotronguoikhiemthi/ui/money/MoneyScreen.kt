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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.trandz123.hotronguoikhiemthi.ml.MoneyClassifier
import com.trandz123.hotronguoikhiemthi.ml.MoneyResult
import com.trandz123.hotronguoikhiemthi.ui.camera.CameraPreviewView
import com.trandz123.hotronguoikhiemthi.ui.camera.LiveMoneyAnalyzer
import com.trandz123.hotronguoikhiemthi.util.hapticStrong
import com.trandz123.hotronguoikhiemthi.util.hapticTick
import com.trandz123.hotronguoikhiemthi.util.toVietnameseMoney
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MoneyClassifierEntryPoint {
    fun moneyClassifier(): MoneyClassifier
}

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
    val mode by viewModel.mode.collectAsState()
    val counting by viewModel.counting.collectAsState()

    val classifier = remember {
        EntryPointAccessors
            .fromApplication(context.applicationContext, MoneyClassifierEntryPoint::class.java)
            .moneyClassifier()
    }

    // Rung manh khi nhan dien duoc tien (ca NORMAL va COUNTING)
    LaunchedEffect(state) {
        val s = state
        when (s) {
            is MoneyUiState.Result -> if (s.moneyResult is MoneyResult.Recognized) hapticStrong(context)
            is MoneyUiState.Counting -> if (s.billCount > 0) hapticStrong(context)
            else -> {}
        }
    }

    val analyzer = remember(classifier) {
        LiveMoneyAnalyzer(
            classifier = classifier,
            onResult = { result -> viewModel.onLiveDetection(result) },
        )
    }

    val gestureHandler: GestureHandler = remember(mode, onSwitchMode, viewModel) {
        GestureHandler(
            onUp = {
                hapticTick(context)
                when (mode) {
                    Mode.NORMAL -> { viewModel.switchToMenu(); onSwitchMode() }
                    Mode.COUNTING -> viewModel.readTotal()
                }
            },
            onDown = {
                hapticTick(context)
                if (mode == Mode.COUNTING) viewModel.resetCount()
            },
            onLeft = {
                hapticTick(context)
                if (mode == Mode.COUNTING) viewModel.exitCountingMode()
            },
            onRight = {
                hapticTick(context)
                when (mode) {
                    Mode.NORMAL -> viewModel.enterCountingMode()
                    Mode.COUNTING -> viewModel.repeatLastAdded()
                }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        CameraPreviewView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gestureHandler) {
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDragEnd = {
                            val absX = kotlin.math.abs(dx)
                            val absY = kotlin.math.abs(dy)
                            val threshold = 100f
                            if (absY > absX && absY > threshold) {
                                if (dy < 0) gestureHandler.onUp() else gestureHandler.onDown()
                            } else if (absX > absY && absX > threshold) {
                                if (dx > 0) gestureHandler.onRight() else gestureHandler.onLeft()
                            }
                        },
                        onDrag = { _, drag -> dx += drag.x; dy += drag.y },
                    )
                }
                .pointerInput(mode, viewModel) {
                    detectTapGestures(
                        onDoubleTap = {
                            hapticTick(context)
                            when (mode) {
                                Mode.NORMAL -> viewModel.repeatLast()
                                Mode.COUNTING -> viewModel.readTotal()
                            }
                        },
                    )
                },
            analyzer = analyzer,
            onCameraReady = { /* ImageCapture khong dung cho money screen */ },
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
                text = if (mode == Mode.COUNTING) "Chế độ đếm cộng dồn" else "Chế độ nhận diện tiền",
                fontSize = 28.sp,
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = statusText(mode, state, counting),
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = hintText(mode),
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private class GestureHandler(
    val onUp: () -> Unit,
    val onDown: () -> Unit,
    val onLeft: () -> Unit,
    val onRight: () -> Unit,
)

private fun statusText(mode: Mode, state: MoneyUiState, counting: CountingState): String =
    when (mode) {
        Mode.NORMAL -> when (state) {
            is MoneyUiState.Analyzing -> "Đang tìm tờ tiền..."
            is MoneyUiState.Result -> state.spokenText
            is MoneyUiState.Counting -> "Đang tìm tờ tiền..."
        }
        Mode.COUNTING -> {
            if (counting.billCount == 0) "Đưa tờ tiền vào để bắt đầu"
            else "${counting.billCount} tờ — ${counting.total.toVietnameseMoney()}"
        }
    }

private fun hintText(mode: Mode): String = when (mode) {
    Mode.NORMAL -> "Vuốt lên: menu. Vuốt phải: đếm cộng dồn."
    Mode.COUNTING -> "Lên: nghe tổng. Xuống: xóa. Trái: thoát. Phải: lặp lại."
}
