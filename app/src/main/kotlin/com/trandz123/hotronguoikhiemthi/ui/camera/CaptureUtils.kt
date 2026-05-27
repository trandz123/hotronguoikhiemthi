package com.trandz123.hotronguoikhiemthi.ui.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import android.content.Context
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Trigger 1 lan capture, tra ve [Bitmap] (da xoay dung orientation).
 * Throw [ImageCaptureException] neu thiet bi khong chup duoc.
 */
suspend fun ImageCapture.captureBitmap(context: Context): Bitmap = suspendCoroutine { cont ->
    takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bmp = image.toBitmapRotated()
                    cont.resume(bmp)
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resumeWithException(exception)
            }
        }
    )
}

private fun ImageProxy.toBitmapRotated(): Bitmap {
    val src = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return src
    val m = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}
