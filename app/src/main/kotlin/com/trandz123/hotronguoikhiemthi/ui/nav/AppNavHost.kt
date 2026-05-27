package com.trandz123.hotronguoikhiemthi.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trandz123.hotronguoikhiemthi.NavEvent
import com.trandz123.hotronguoikhiemthi.ui.history.HistoryScreen
import com.trandz123.hotronguoikhiemthi.ui.home.HomeScreen
import com.trandz123.hotronguoikhiemthi.ui.menu.MenuScreen
import com.trandz123.hotronguoikhiemthi.ui.money.MoneyScreen
import com.trandz123.hotronguoikhiemthi.ui.settings.SettingsScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val nav = rememberNavController()

    // Listen voice command / shake events from MainActivity
    LaunchedEffect(Unit) {
        NavEventBus.events.collect { event ->
            when (event) {
                NavEvent.GoHome -> nav.popBackStack(Route.Home.path, inclusive = false)
                NavEvent.GoMoney -> nav.navigate(Route.Money.path) { launchSingleTop = true }
                NavEvent.GoMenu -> nav.navigate(Route.Menu.path) { launchSingleTop = true }
                NavEvent.GoHistory -> nav.navigate(Route.History.path) { launchSingleTop = true }
                NavEvent.GoSettings -> nav.navigate(Route.Settings.path) { launchSingleTop = true }
                NavEvent.Repeat -> Unit // ViewModel cua screen xu ly
            }
        }
    }

    NavHost(
        navController = nav,
        // UX accessibility: bo qua Home screen, mo thang MenuScreen.
        // Nguoi khiem thi khong the "click" chon mode → app default mode menu.
        // Doi mode bang gesture (swipe len) hoac voice command ("doc tien").
        startDestination = Route.Menu.path,
        modifier = modifier,
    ) {
        composable(Route.Home.path) {
            HomeScreen(
                onReadMoneyClick = { nav.navigate(Route.Money.path) },
                onReadMenuClick = { nav.navigate(Route.Menu.path) },
                onSettingsClick = { nav.navigate(Route.Settings.path) },
                onHistoryClick = { nav.navigate(Route.History.path) },
            )
        }
        composable(Route.Money.path) {
            MoneyScreen(
                onBack = { nav.navigate(Route.Home.path) { launchSingleTop = true } },
                onSwitchMode = { nav.navigate(Route.Menu.path) { launchSingleTop = true } },
            )
        }
        composable(Route.Menu.path) {
            MenuScreen(
                onBack = { nav.navigate(Route.Home.path) { launchSingleTop = true } },
                onSwitchMode = { nav.navigate(Route.Money.path) { launchSingleTop = true } },
            )
        }
        composable(Route.Settings.path) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.History.path) {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
    }
}
