package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.sqrt

data class HorizonTiltState(
    val rollDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val isSensorAvailable: Boolean = true
)

/**
 * Provides real-time roll and pitch angles from the device's accelerometer
 * for the electronic Horizon Line guide overlay.
 */
class HorizonSensorManager(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun observeTilt(): Flow<HorizonTiltState> = callbackFlow {
        val mgr = sensorManager
        val sensor = accelerometer
        if (mgr == null || sensor == null) {
            trySend(HorizonTiltState(0f, 0f, isSensorAvailable = false))
            awaitClose { }
            return@callbackFlow
        }

        var smoothedRoll = 0f
        var smoothedPitch = 0f
        val alpha = 0.18f // Low-pass filter for smooth cinema horizon line

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return
                if (values.size < 3) return
                val ax = values[0]
                val ay = values[1]
                val az = values[2]

                // Calculate device roll (left/right tilt in degrees) and pitch (forward/back tilt)
                val rawRoll = Math.toDegrees(atan2(-ax.toDouble(), ay.toDouble())).toFloat()
                val norm = sqrt((ax * ax + ay * ay + az * az).toDouble()).coerceAtLeast(0.001)
                val rawPitch = Math.toDegrees( kotlin.math.asin((az / norm).coerceIn(-1.0, 1.0)) ).toFloat()

                smoothedRoll += alpha * (rawRoll - smoothedRoll)
                smoothedPitch += alpha * (rawPitch - smoothedPitch)

                trySend(
                    HorizonTiltState(
                        rollDegrees = smoothedRoll.coerceIn(-90f, 90f),
                        pitchDegrees = smoothedPitch.coerceIn(-90f, 90f),
                        isSensorAvailable = true
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose {
            mgr.unregisterListener(listener)
        }
    }
}
