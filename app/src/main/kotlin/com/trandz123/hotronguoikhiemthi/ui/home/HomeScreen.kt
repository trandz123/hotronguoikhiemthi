package com.trandz123.hotronguoikhiemthi.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
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

/**
 * Man hinh chinh. UI accessibility-first:
 *  - 3 nut lon, cao 96dp (toi thieu 64dp theo WCAG)
 *  - Chu to 28sp, tuong phan cao
 *  - Moi nut co contentDescription mo ta hanh dong + huong dan
 *  - Icon co semantic null de TalkBack khong doc 2 lan
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onReadMoneyClick: () -> Unit,
    onReadMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
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
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        BigActionButton(
            label = stringResource(R.string.action_read_money),
            description = stringResource(R.string.action_read_money_desc),
            icon = { iconModifier ->
                Icon(
                    imageVector = Icons.Default.AttachMoney,
                    contentDescription = null,
                    modifier = iconModifier,
                )
            },
            onClick = onReadMoneyClick,
        )

        BigActionButton(
            label = stringResource(R.string.action_read_menu),
            description = stringResource(R.string.action_read_menu_desc),
            icon = { iconModifier ->
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = iconModifier,
                )
            },
            onClick = onReadMenuClick,
        )

        BigActionButton(
            label = stringResource(R.string.action_settings),
            description = stringResource(R.string.action_settings_desc),
            icon = { iconModifier ->
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = iconModifier,
                )
            },
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun BigActionButton(
    label: String,
    description: String,
    icon: @Composable (Modifier) -> Unit,
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
        icon(Modifier.size(40.dp))
        Spacer(Modifier.size(16.dp))
        Text(
            text = label,
            fontSize = 28.sp,
        )
    }
}
