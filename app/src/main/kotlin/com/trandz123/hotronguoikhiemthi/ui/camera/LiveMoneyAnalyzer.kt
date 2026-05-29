package com.trandz123.hotronguoikhiemthi.ui.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.trandz123.hotronguoikhiemthi.ml.MoneyClassifier
import com.trandz123.hotronguoikhiemthi.ml.MoneyResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live inference YOLOv10n tren tung frame camera. Khong cho user "chup" — phu hop UX
 * khiem thi: dua tien vao khung hinh la nhan duoc ngay.
 *
 * Throttling:
 *  - Backpressure CameraX: STRATEGY_KEEP_ONLY_LATEST → chi frame moi nhat duoc dua vao.
 *  - [busy] flag: bo qua frame moi neu inference cu chua xong (tranh queue overload).
 *
 * Frame ImageProxy → Bitmap RGB → rotate theo rotationDegrees → MoneyClassifier.classify
 * → callback `onResult(MoneyResult)` cho ViewModel.
 *
 * ViewModel chiu trach nhiem stability filter (cung class N frame lien tiep) + cooldown.
 */
class LiveMoneyAnalyzer(
    private val classifier: MoneyClassifier,
    private val onResult: (MoneyResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val busy = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        val bitmap = try {
            val raw = image.toBitmap()
            val rot = image.imageInfo.rotationDegrees
            if (rot != 0) raw.rotate(rot) else raw
        } catch (_: Throwable) {
            busy.set(false)
            image.close()
            return
        } finally {
            image.close()
        }

        scope.launch {
            try {
                val result = classifier.classify(bitmap)
                onResult(result)
            } catch (_: Throwable) {
                // Silent: 1 frame fail khong block ca pipeline
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                busy.set(false)
            }
        }
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, m, true)
        if (rotated != this) recycle()
        return rotated
    }
}
