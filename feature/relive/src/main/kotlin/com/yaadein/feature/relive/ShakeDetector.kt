package com.yaadein.feature.relive

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.sqrt

/** Pure shake logic (unit-testable): a shake is ≥ [threshold] g-force spikes, [requiredSpikes] times within [windowMs]. */
class ShakeLogic(private val threshold: Float = 2.6f, private val requiredSpikes: Int = 3, private val windowMs: Long = 600, private val cooldownMs: Long = 2_000) {
    private var spikes = 0
    private var firstSpikeAt = 0L
    private var lastShakeAt = Long.MIN_VALUE / 2

    /** Returns true when a shake completes. [gForce] = |a| / 9.81. */
    fun onSample(gForce: Float, nowMs: Long): Boolean {
        if (gForce < threshold) return false
        if (nowMs - firstSpikeAt > windowMs) { spikes = 0; firstSpikeAt = nowMs }
        spikes++
        if (spikes >= requiredSpikes && nowMs - lastShakeAt > cooldownMs) {
            lastShakeAt = nowMs; spikes = 0
            return true
        }
        return false
    }
}

/**
 * Listens to the accelerometer while the screen is showing; calls [onShake] on the main thread. The sensor is released
 * as soon as the app is paused, so it never keeps sampling (and draining the battery) behind another app.
 */
@Composable
fun ShakeListener(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onShake)
    LifecycleResumeEffect(enabled, context) {
        if (!enabled) return@LifecycleResumeEffect onPauseOrDispose { }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@LifecycleResumeEffect onPauseOrDispose { }
        val logic = ShakeLogic()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0] / SensorManager.GRAVITY_EARTH
                val y = event.values[1] / SensorManager.GRAVITY_EARTH
                val z = event.values[2] / SensorManager.GRAVITY_EARTH
                if (logic.onSample(sqrt(x * x + y * y + z * z), System.currentTimeMillis())) callback.value()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose { manager.unregisterListener(listener) }
    }
}
