package com.trandz123.hotronguoikhiemthi.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trandz123.hotronguoikhiemthi.ui.home.HomeScreen
import com.trandz123.hotronguoikhiemthi.ui.menu.MenuScreen
import com.trandz123.hotronguoikhiemthi.ui.money.MoneyScreen
import com.trandz123.hotronguoikhiemthi.ui.settings.SettingsScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = Route.Home.path,
        modifier = modifier,
    ) {
        composable(Route.Home.path) {
            HomeScreen(
                onReadMoneyClick = { nav.navigate(Route.Money.path) },
                onReadMenuClick = { nav.navigate(Route.Menu.path) },
                onSettingsClick = { nav.navigate(Route.Settings.path) },
            )
        }
        composable(Route.Money.path) {
            MoneyScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.Menu.path) {
            MenuScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.Settings.path) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
