package com.trandz123.hotronguoikhiemthi.ui.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Phan tich nhanh chat luong khung hinh tu CameraX preview de quyet dinh
 * co nen auto-capture chua. Chay tren thread cua CameraX (background).
 *
 * Heuristic:
 *  - Sang (avg Y plane): > 80/255 (~tham chieu indoor)
 *  - Net (Laplacian variance tren Y subsample): > 100
 *
 * 2 dieu kien deu pass trong N frame lien tiep → on dinh, callback `onStable`.
 */
class FrameQualityAnalyzer(
    private val requiredStableFrames: Int = 3,
    private val onResult: (FrameQuality) -> Unit,
    private val onStable: () -> Unit,
) : ImageAnalysis.Analyzer {

    private var consecutiveGood = 0

    override fun analyze(image: ImageProxy) {
        try {
            val q = computeQuality(image)
            onResult(q)
            if (q.isGoodEnough) {
                consecutiveGood += 1
                if (consecutiveGood >= requiredStableFrames) {
                    consecutiveGood = 0
                    onStable()
                }
            } else {
                consecutiveGood = 0
            }
        } finally {
            image.close()
        }
    }

    private fun computeQuality(image: ImageProxy): FrameQuality {
        val yPlane = image.planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = image.width
        val height = image.height

        // Subsample 1/10 cho nhanh
        val stepX = 10
        val stepY = 10
        var sum = 0L
        var count = 0
        val row = ByteArray(rowStride)

        // Doc 1 lan vao mang tam → tinh brightness va sharpness
        val ySamples = ArrayList<Int>(width * height / (stepX * stepY) + 16)
        val originalPos = buffer.position()
        buffer.rewind()
        var y = 0
        while (y < height) {
            // Position vao dau dong y
            buffer.position(y * rowStride)
            val toRead = minOf(rowStride, buffer.remaining())
            buffer.get(row, 0, toRead)
            var x = 0
            while (x < width && x < toRead) {
                val v = row[x].toInt() and 0xFF
                sum += v
                count++
                ySamples.add(v)
                x += stepX
            }
            y += stepY
        }
        buffer.position(originalPos)

        val avg = if (count > 0) sum.toDouble() / count else 0.0

        // Laplacian variance tren mang da subsample (1D approx — du de detect blur)
        val variance = if (ySamples.size >= 3) {
            var meanLap = 0.0
            var meanSqLap = 0.0
            var n = 0
            for (i in 1 until ySamples.size - 1) {
                val lap = (ySamples[i + 1] - 2 * ySamples[i] + ySamples[i - 1]).toDouble()
                meanLap += lap
                meanSqLap += lap * lap
                n++
            }
            if (n > 0) {
                meanLap /= n
                meanSqLap /= n
                meanSqLap - meanLap * meanLap
            } else 0.0
        } else 0.0

        return FrameQuality(
            brightness = avg,
            sharpness = variance,
            isGoodEnough = avg >= MIN_BRIGHTNESS && variance >= MIN_SHARPNESS,
        )
    }

    companion object {
        const val MIN_BRIGHTNESS = 80.0
        const val MIN_SHARPNESS = 100.0
    }
}

data class FrameQuality(
    val brightness: Double,
    val sharpness: Double,
    val isGoodEnough: Boolean,
)
