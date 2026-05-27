package com.trandz123.hotronguoikhiemthi.voice

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Phat hien lac dien thoai → panic stop TTS. Threshold cao de tranh false-positive
 * khi nguoi dung di lai/cam dien thoai dang chup.
 *
 * Algorithm:
 *  - Tinh magnitude sqrt(x^2+y^2+z^2) - gravity
 *  - Neu > 25 m/s^2 (cao hon binh thuong nhieu) → emit shake event
 *  - Debounce 800ms de moi lan lac chi trigger 1 lan
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeMs = 0L

    fun start() {
        accelerometer?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = e.values[0]
        val y = e.values[1]
        val z = e.values[2]
        val magnitude = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
        if (magnitude > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastShakeMs > DEBOUNCE_MS) {
                lastShakeMs = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SHAKE_THRESHOLD = 15f
        const val DEBOUNCE_MS = 800L
    }
}
