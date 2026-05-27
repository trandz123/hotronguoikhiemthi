package com.trandz123.hotronguoikhiemthi.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trandz123.hotronguoikhiemthi.data.history.ScanType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val items by viewModel.recent.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Lịch sử quét",
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Chạm vào 1 mục để nghe lại. Tối đa 20 lần quét gần nhất.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Chưa có lần quét nào",
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items) { entity ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "${entity.type.label()} ngày ${formatDate(entity.timestampMs)}. ${entity.spokenText}. Chạm đôi để nghe lại."
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    entity.type.label(),
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    formatDate(entity.timestampMs),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                entity.spokenText,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Button(
                                onClick = { viewModel.replay(entity) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(top = 8.dp),
                            ) { Text("Nghe lại", fontSize = 18.sp) }
                        }
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (items.isNotEmpty()) {
                Button(
                    onClick = { viewModel.clearAll() },
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .semantics { contentDescription = "Xóa toàn bộ lịch sử" },
                ) { Text("Xóa hết", fontSize = 20.sp) }
            }
            Button(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
            ) { Text("← Quay lại", fontSize = 20.sp) }
        }
    }
}

private fun ScanType.label(): String = when (this) {
    ScanType.MONEY -> "Đọc tiền"
    ScanType.MENU -> "Đọc menu"
}

private val DATE_FORMAT = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale("vi", "VN"))

private fun formatDate(ms: Long): String = DATE_FORMAT.format(Date(ms))
