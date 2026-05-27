package com.trandz123.hotronguoikhiemthi.ui.nav

import com.trandz123.hotronguoikhiemthi.NavEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridge tu MainActivity (voice command, key event) → [AppNavHost] (con compose-side).
 * Tranh phai chuyen navController ra ngoai. Khong cau hinh qua Hilt vi day la in-process
 * event bus don gian (singleton object).
 */
object NavEventBus {

    private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<NavEvent> = _events.asSharedFlow()

    fun emit(event: NavEvent) {
        _events.tryEmit(event)
    }
}
