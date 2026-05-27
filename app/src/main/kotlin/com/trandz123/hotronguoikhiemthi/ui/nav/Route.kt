package com.trandz123.hotronguoikhiemthi.ui.nav

/**
 * Tap trung route string de tranh typo. Sealed cho compiler check exhaustiveness.
 *
 * Khi them man hinh moi: them entry o day + handle trong [AppNavHost].
 */
sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Money : Route("money")
    data object Menu : Route("menu")
    data object Settings : Route("settings")
    data object History : Route("history")
}
