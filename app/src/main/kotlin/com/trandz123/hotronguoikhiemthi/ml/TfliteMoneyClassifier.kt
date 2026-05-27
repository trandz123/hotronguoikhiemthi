package com.trandz123.hotronguoikhiemthi.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

/**
 * Production classifier dung TFLite. Load model tu `assets/ml/vnd_classifier.tflite`.
 *
 *  - Input shape: [1, 224, 224, 3], float32, normalize [0,1]
 *  - Output shape: [1, 10], float32 (softmax probabilities, 10 class theo MONEY_LABELS)
 *
 * Neu file model chua co (chua train xong tuan 3-4), [init] se throw va Hilt fallback Fake.
 */
class TfliteMoneyClassifier(context: Context) : MoneyClassifier {

    private val interpreter: Interpreter
    private val processor: ImageProcessor

    init {
        val modelBuffer = FileUtil.loadMappedFile(context, MODEL_PATH)
        val options = Interpreter.Options().apply { setNumThreads(4) }
        interpreter = Interpreter(modelBuffer, options)
        processor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        Log.i(TAG, "TFLite model loaded from $MODEL_PATH")
    }

    override suspend fun classify(bitmap: Bitmap): MoneyResult = withContext(Dispatchers.Default) {
        val input = processor.process(TensorImage.fromBitmap(bitmap))
        val output = Array(1) { FloatArray(MONEY_LABELS.size) }
        interpreter.run(input.buffer, output)

        val probs = output[0]
        val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: return@withContext MoneyResult.Unknown
        val maxProb = probs[maxIdx]
        val label = MONEY_LABELS[maxIdx]

        when {
            maxProb < MIN_CONFIDENCE -> MoneyResult.Unknown
            label.isUnknown -> MoneyResult.Unknown
            else -> MoneyResult.Recognized(label.denominationVnd, maxProb)
        }
    }

    override fun close() {
        interpreter.close()
    }

    private companion object {
        const val TAG = "TfliteMoneyClassifier"
        const val MODEL_PATH = "ml/vnd_classifier.tflite"
        const val INPUT_SIZE = 224
    }
}
