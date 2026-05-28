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
 * YOLOv10n detector cho tien VND. Train tu Roboflow dataset (tien gap/vo/che).
 *
 *  - Input: `[1, 640, 640, 3]` FLOAT32, normalize pixel/255.0 (range 0..1)
 *  - Output: `[1, MAX_DET, 6]` FLOAT32 — moi row = [x1, y1, x2, y2, conf, class_id]
 *    Detection sap xep theo conf giam dan, padding bang 0 neu < MAX_DET.
 *
 * Trien khai [MoneyClassifier]: tra ve detection co conf cao nhat (dau output[0][0]).
 * Neu conf < threshold -> Unknown.
 *
 * Neu file model thieu hoac corrupted -> [init] throw -> Hilt module fallback sang
 * [TfliteMoneyClassifier] hoac [FakeMoneyClassifier].
 */
class Yolov10MoneyDetector(context: Context) : MoneyClassifier {

    private val interpreter: Interpreter
    private val processor: ImageProcessor
    private val outputBuffer: Array<Array<FloatArray>>

    init {
        val modelBuffer = FileUtil.loadMappedFile(context, MODEL_PATH)
        val options = Interpreter.Options().apply { setNumThreads(4) }
        interpreter = Interpreter(modelBuffer, options)
        // Resize 640x640, normalize 0..1 (chia 255)
        processor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        val outShape = interpreter.getOutputTensor(0).shape()  // [1, MAX_DET, 6]
        require(outShape.size == 3 && outShape[2] == 6) {
            "Unexpected YOLOv10 output shape: ${outShape.joinToString()}"
        }
        outputBuffer = Array(outShape[0]) { Array(outShape[1]) { FloatArray(outShape[2]) } }
        Log.i(TAG, "YOLOv10n loaded, output shape=${outShape.joinToString()}")
    }

    override suspend fun classify(bitmap: Bitmap): MoneyResult = withContext(Dispatchers.Default) {
        val tensorImage = TensorImage(DataType.FLOAT32).apply { load(bitmap) }
        val input = processor.process(tensorImage)
        interpreter.run(input.buffer, outputBuffer)

        // outputBuffer[0] = [MAX_DET][6]. YOLOv10 da NMS + sort conf giam.
        // Lay detection co conf cao nhat (row 0). Bo qua row co conf=0 (padding).
        val detections = outputBuffer[0]
        val top = detections.firstOrNull { it[4] > 0f } ?: return@withContext MoneyResult.Unknown
        val conf = top[4]
        val classId = top[5].toInt()

        if (conf < MIN_CONFIDENCE) return@withContext MoneyResult.Unknown
        if (classId !in 0..MONEY_LABELS.lastIndex) return@withContext MoneyResult.Unknown
        val label = MONEY_LABELS[classId]
        if (label.isUnknown) return@withContext MoneyResult.Unknown
        MoneyResult.Recognized(label.denominationVnd, conf)
    }

    override fun close() {
        interpreter.close()
    }

    private companion object {
        const val TAG = "Yolov10MoneyDetector"
        const val MODEL_PATH = "ml/vnd_yolov10n.tflite"
        const val INPUT_SIZE = 640
    }
}
