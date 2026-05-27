package com.trandz123.hotronguoikhiemthi.ml

import android.graphics.Bitmap
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stub dung de phat trien UI khi chua co model .tflite that.
 * Cycle qua 9 menh gia theo thu tu de demo flow.
 *
 * Co the dung trong UI test deterministic.
 */
class FakeMoneyClassifier : MoneyClassifier {

    private val cycle = AtomicInteger(0)
    private val sequence = MONEY_LABELS.filter { !it.isUnknown }

    override suspend fun classify(bitmap: Bitmap): MoneyResult {
        // Gia lap thoi gian inference ~150ms cua model that
        delay(150)
        val idx = (cycle.getAndIncrement() % sequence.size).coerceAtLeast(0)
        val label = sequence[idx]
        return MoneyResult.Recognized(label.denominationVnd, confidence = 0.95f)
    }

    override fun close() = Unit
}
