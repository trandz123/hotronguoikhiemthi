package com.trandz123.hotronguoikhiemthi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trandz123.hotronguoikhiemthi.ui.home.HomeScreen
import com.trandz123.hotronguoikhiemthi.ui.theme.HoTroTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HoTroTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        HomeScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            onReadMoneyClick = { /* TODO: navigate to MoneyScreen */ },
            onReadMenuClick = { /* TODO: navigate to MenuScreen */ },
            onSettingsClick = { /* TODO: navigate to SettingsScreen */ }
        )
    }
}
