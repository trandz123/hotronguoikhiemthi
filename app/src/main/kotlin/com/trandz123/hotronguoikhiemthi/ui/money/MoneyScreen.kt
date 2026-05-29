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
import com.trandz123.hotronguoikhiemthi.ml.MoneyClassifier
import com.trandz123.hotronguoikhiemthi.ml.MoneyResult
import com.trandz123.hotronguoikhiemthi.ui.camera.CameraPreviewView
import com.trandz123.hotronguoikhiemthi.ui.camera.LiveMoneyAnalyzer
import com.trandz123.hotronguoikhiemthi.util.hapticStrong
import com.trandz123.hotronguoikhiemthi.util.hapticTick
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

    // Lay classifier qua EntryPoint (Hilt @Singleton — share voi MoneyViewModel khong injected truc tiep)
    val classifier = remember {
        EntryPointAccessors
            .fromApplication(context.applicationContext, MoneyClassifierEntryPoint::class.java)
            .moneyClassifier()
    }

    // Rung manh khi nhan dien duoc tien.
    LaunchedEffect(state) {
        val s = state
        if (s is MoneyUiState.Result && s.moneyResult is MoneyResult.Recognized) {
            hapticStrong(context)
        }
    }

    val analyzer = remember(classifier) {
        LiveMoneyAnalyzer(
            classifier = classifier,
            onResult = { result -> viewModel.onLiveDetection(result) },
        )
    }

    val swipeUpToMenu: () -> Unit = remember(onSwitchMode, viewModel) {
        {
            hapticTick(context)
            viewModel.switchToMenu()
            onSwitchMode()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CameraPreviewView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(swipeUpToMenu) {
                    // UX: vuot LEN duy nhat = chuyen sang Menu mode.
                    // Khong xu ly vuot xuong (live YOLO tu reset, khong can scanAgain thu cong).
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDragEnd = {
                            val absX = kotlin.math.abs(dx)
                            val absY = kotlin.math.abs(dy)
                            if (absY > absX && absY > 100f && dy < 0) swipeUpToMenu()
                        },
                        onDrag = { _, drag -> dx += drag.x; dy += drag.y },
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
                "Vuốt lên để chuyển sang đọc menu.",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun statusText(state: MoneyUiState): String = when (state) {
    is MoneyUiState.Analyzing -> "Đang tìm tờ tiền..."
    is MoneyUiState.Result -> state.spokenText
}
