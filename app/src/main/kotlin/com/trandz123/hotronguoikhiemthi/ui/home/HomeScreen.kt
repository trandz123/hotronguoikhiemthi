package com.trandz123.hotronguoikhiemthi.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trandz123.hotronguoikhiemthi.R

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onReadMoneyClick: () -> Unit,
    onReadMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontSize = 36.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        Text(
            text = stringResource(R.string.home_hint),
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        BigActionButton(
            label = stringResource(R.string.action_read_money),
            description = stringResource(R.string.action_read_money_desc),
            icon = Icons.Default.AttachMoney,
            onClick = onReadMoneyClick,
        )

        BigActionButton(
            label = stringResource(R.string.action_read_menu),
            description = stringResource(R.string.action_read_menu_desc),
            icon = Icons.Default.MenuBook,
            onClick = onReadMenuClick,
        )

        BigActionButton(
            label = "Lịch sử",
            description = "Xem lại các lần quét gần đây",
            icon = Icons.Default.History,
            onClick = onHistoryClick,
        )

        BigActionButton(
            label = stringResource(R.string.action_settings),
            description = stringResource(R.string.action_settings_desc),
            icon = Icons.Default.Settings,
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun BigActionButton(
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics { contentDescription = "$label. $description" },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(text = label, fontSize = 28.sp)
    }
}
