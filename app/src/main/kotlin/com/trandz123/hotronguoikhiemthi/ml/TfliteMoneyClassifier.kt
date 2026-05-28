package com.trandz123.hotronguoikhiemthi.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

/**
 * Production classifier dung TFLite. Load model tu `assets/ml/vnd_classifier.tflite`.
 *
 *  - Input shape: [1, 224, 224, 3], FLOAT32, ImageNet normalization
 *    (pixel - mean*255) / (std*255), mean=[0.485,0.456,0.406], std=[0.229,0.224,0.225]
 *  - Output shape: [1, 10], float32 (logits, 10 class theo MONEY_LABELS)
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
        // Model train voi PyTorch + ImageNet normalization → resize + chuan hoa (pixel - mean*255) / (std*255)
        val mean = floatArrayOf(0.485f * 255f, 0.456f * 255f, 0.406f * 255f)
        val std = floatArrayOf(0.229f * 255f, 0.224f * 255f, 0.225f * 255f)
        processor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(mean, std))
            .build()
        Log.i(TAG, "TFLite model loaded from $MODEL_PATH")
    }

    override suspend fun classify(bitmap: Bitmap): MoneyResult = withContext(Dispatchers.Default) {
        val tensorImage = TensorImage(DataType.FLOAT32).apply { load(bitmap) }
        val input = processor.process(tensorImage)
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
