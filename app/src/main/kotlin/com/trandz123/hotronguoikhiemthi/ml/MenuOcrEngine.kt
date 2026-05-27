package com.trandz123.hotronguoikhiemthi.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wrap ML Kit Text Recognition v2 (Latin script - ho tro tieng Viet co dau).
 * Chay on-device, khong can mang sau khi model duoc tai (lan dau tien chay app).
 */
@Singleton
class MenuOcrEngine @Inject constructor() {

    private val recognizer: TextRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    /**
     * Chay OCR tren bitmap, parse thanh [MenuItem]. Suspend cho den khi xong.
     */
    suspend fun extract(bitmap: Bitmap): List<MenuItem> = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val items = MenuOcrParser.parse(text)
                cont.resume(items)
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    fun close() {
        recognizer.close()
    }
}
