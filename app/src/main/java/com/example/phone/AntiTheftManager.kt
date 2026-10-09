package com.example.phone

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

enum class AntiTheftAlertState {
    DISARMED,
    ARMED_MONITORING,
    WARNING_1,
    WARNING_2,
    ALARM_ACTIVE
}

enum class AntiTheftSensitivity(val label: String, val threshold: Float) {
    LOW("Low (Heavy movement)", 5.5f),
    MEDIUM("Medium (Lifting/Tilting)", 3.2f),
    HIGH("High (Slight touch)", 1.8f)
}

/**
 * Real Android accelerometer-based Anti-Theft & Owner Protection Manager.
 * Complies with Section 15 of SANA V4 Specification.
 */
class AntiTheftManager(
    private val context: Context,
    private val onTriggerSpokenWarning: (warningNumber: Int, message: String) -> Unit
) : SensorEventListener {

    private val tag = "AntiTheftManager"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _alertState = MutableStateFlow(AntiTheftAlertState.DISARMED)
    val alertState: StateFlow<AntiTheftAlertState> = _alertState.asStateFlow()

    private val _isArmed = MutableStateFlow(false)
    val isArmed: StateFlow<Boolean> = _isArmed.asStateFlow()

    private var currentSensitivity = AntiTheftSensitivity.MEDIUM
    private val handler = Handler(Looper.getMainLooper())

    private var lastGravity = floatArrayOf(0f, 0f, 0f)
    private var hasBaseline = false
    private var sustainedMovementCount = 0
    private var lastWarningTime = 0L

    private var toneGenerator: ToneGenerator? = null
    private var alarmRunnable: Runnable? = null

    companion object {
        const val WARNING_1_MESSAGE = "This is my Boss’s phone. Please put it back."
        const val WARNING_2_MESSAGE = "Warning! This phone belongs to my Boss. Put it down immediately."
    }

    fun setSensitivity(sensitivity: AntiTheftSensitivity) {
        currentSensitivity = sensitivity
    }

    /**
     * Arms the anti-theft protection system.
     */
    fun armProtection(): Boolean {
        if (accelerometer == null) {
            Log.w(tag, "Accelerometer sensor not found on this device.")
            return false
        }

        hasBaseline = false
        sustainedMovementCount = 0
        _isArmed.value = true
        _alertState.value = AntiTheftAlertState.ARMED_MONITORING

        sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
        Log.i(tag, "SANA Anti-Theft Protection Armed. Sensitivity: ${currentSensitivity.label}")
        return true
    }

    /**
     * Disarms the anti-theft protection system.
     */
    fun disarmProtection() {
        _isArmed.value = false
        _alertState.value = AntiTheftAlertState.DISARMED
        sensorManager?.unregisterListener(this)
        stopAlarmSound()
        handler.removeCallbacksAndMessages(null)
        Log.i(tag, "SANA Anti-Theft Protection Disarmed by Boss.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!_isArmed.value || event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (!hasBaseline) {
            lastGravity = floatArrayOf(x, y, z)
            hasBaseline = true
            return
        }

        // Calculate dynamic delta from baseline (filtering out constant gravity)
        val deltaX = x - lastGravity[0]
        val deltaY = y - lastGravity[1]
        val deltaZ = z - lastGravity[2]
        val deltaMagnitude = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)

        // Slowly adapt baseline to avoid drift when stationary
        lastGravity[0] = lastGravity[0] * 0.9f + x * 0.1f
        lastGravity[1] = lastGravity[1] * 0.9f + y * 0.1f
        lastGravity[2] = lastGravity[2] * 0.9f + z * 0.1f

        if (deltaMagnitude > currentSensitivity.threshold) {
            sustainedMovementCount++
            // Require at least 3 consecutive movement readings to rule out random table bumps
            if (sustainedMovementCount >= 3) {
                handleSuspiciousMovementDetected()
            }
        } else {
            if (sustainedMovementCount > 0) {
                sustainedMovementCount--
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleSuspiciousMovementDetected() {
        val now = System.currentTimeMillis()
        if (now - lastWarningTime < 3500) return // Debounce rapid triggers
        lastWarningTime = now

        when (_alertState.value) {
            AntiTheftAlertState.ARMED_MONITORING -> {
                Log.w(tag, "Suspicious movement detected! Issuing Warning 1.")
                _alertState.value = AntiTheftAlertState.WARNING_1
                triggerVibration()
                onTriggerSpokenWarning(1, WARNING_1_MESSAGE)

                // Schedule transition to Warning 2 if movement persists after 5 seconds
                handler.postDelayed({
                    if (_isArmed.value && _alertState.value == AntiTheftAlertState.WARNING_1 && sustainedMovementCount >= 2) {
                        issueWarning2()
                    }
                }, 5000)
            }

            AntiTheftAlertState.WARNING_1 -> {
                issueWarning2()
            }

            AntiTheftAlertState.WARNING_2 -> {
                issueAlarmState()
            }

            AntiTheftAlertState.ALARM_ACTIVE, AntiTheftAlertState.DISARMED -> {
                // Already in alarm or disarmed
            }
        }
    }

    private fun issueWarning2() {
        Log.w(tag, "Movement continued! Issuing Warning 2.")
        _alertState.value = AntiTheftAlertState.WARNING_2
        triggerVibration()
        onTriggerSpokenWarning(2, WARNING_2_MESSAGE)

        // Schedule escalation to Alarm if continued movement
        handler.postDelayed({
            if (_isArmed.value && _alertState.value == AntiTheftAlertState.WARNING_2 && sustainedMovementCount >= 2) {
                issueAlarmState()
            }
        }, 5000)
    }

    private fun issueAlarmState() {
        Log.w(tag, "Unauthorized handling persisted! Escalating to ALARM_ACTIVE.")
        _alertState.value = AntiTheftAlertState.ALARM_ACTIVE
        startAlarmSound()
    }

    private fun startAlarmSound() {
        try {
            toneGenerator?.release()
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

            alarmRunnable = object : Runnable {
                override fun run() {
                    if (_alertState.value == AntiTheftAlertState.ALARM_ACTIVE) {
                        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 700)
                        triggerVibration()
                        handler.postDelayed(this, 1000)
                    }
                }
            }
            alarmRunnable?.run()
        } catch (e: Exception) {
            Log.e(tag, "Failed to start alarm sound: ${e.message}")
        }
    }

    private fun stopAlarmSound() {
        alarmRunnable?.let { handler.removeCallbacks(it) }
        alarmRunnable = null
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(tag, "Error releasing tone generator", e)
        }
    }

    private fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } catch (e: Exception) {
            Log.d(tag, "Vibration not supported or denied: ${e.message}")
        }
    }
}
