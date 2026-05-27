package com.trandz123.hotronguoikhiemthi.ml

import android.graphics.Bitmap

/**
 * Phan loai 1 anh tien VND → 1 trong 9 menh gia hoac UNKNOWN.
 *
 * Implementation:
 *  - [TfliteMoneyClassifier]: dung vnd_classifier.tflite (production)
 *  - [FakeMoneyClassifier]: cycle qua 9 menh gia (dung khi chua co model thuc / chay UI test)
 *
 * Hilt binding: thu TFLite truoc. Neu init that bai (assets thieu .tflite), fallback Fake.
 */
interface MoneyClassifier {

    /**
     * Phan loai 1 bitmap (kich thuoc bat ky — implementation se resize ve 224x224).
     *
     * @return [MoneyResult.Recognized] neu confidence ≥ threshold,
     *         [MoneyResult.Unknown] neu thap hon.
     */
    suspend fun classify(bitmap: Bitmap): MoneyResult

    fun close()
}

sealed class MoneyResult {
    data class Recognized(
        /** Menh gia VND (1000, 2000, ..., 500000). */
        val denominationVnd: Long,
        /** Confidence trong khoang [0,1]. */
        val confidence: Float,
    ) : MoneyResult()

    /** Confidence thap hoac model tra class "unknown". */
    data object Unknown : MoneyResult()
}

/**
 * Lop output cua model (theo thu tu trong vnd_labels.txt).
 * INDEX phai trung voi file labels.txt → assets/ml/vnd_labels.txt.
 */
val MONEY_LABELS: List<MoneyLabel> = listOf(
    MoneyLabel(500_000L, "500000"),
    MoneyLabel(200_000L, "200000"),
    MoneyLabel(100_000L, "100000"),
    MoneyLabel(50_000L, "50000"),
    MoneyLabel(20_000L, "20000"),
    MoneyLabel(10_000L, "10000"),
    MoneyLabel(5_000L, "5000"),
    MoneyLabel(2_000L, "2000"),
    MoneyLabel(1_000L, "1000"),
    MoneyLabel(0L, "unknown"),
)

data class MoneyLabel(val denominationVnd: Long, val rawLabel: String) {
    val isUnknown: Boolean get() = denominationVnd == 0L
}

/** Nguong tin cay toi thieu. Duoi nguong → Unknown. */
const val MIN_CONFIDENCE = 0.70f
