package com.sauban.stepcounter

import android.app.PendingIntent
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.sauban.stepcounter.data.StepEngineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TRANSITIONS_ACTION = "com.sauban.stepcounter.ACTIVITY_TRANSITION"
private const val TRANSITIONS_REQUEST_CODE = 4210
class HybridStepEngine : SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var activityRecognitionClient: ActivityRecognitionClient? = null
    private var registeredContext: Context? = null
    private var pendingIntent: PendingIntent? = null
    private var receiver: BroadcastReceiver? = null

    private var lastRawTotal = -1L
    private var sessionSteps = 0
    private var statusMessage = "Waiting for the step sensor..."

    @Volatile private var currentActivityType: Int = DetectedActivity.UNKNOWN
    @Volatile private var activityRecognitionReady = false

    private var sensorPresent = true
    private var activityRecognitionAvailable = true

    private var onUpdate: ((StepEngineState) -> Unit)? = null

    fun start(context: Context, callback: (StepEngineState) -> Unit) {
        onUpdate = callback
        registeredContext = context.applicationContext
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        val activeSensor = stepSensor
        if (activeSensor == null) {
            sensorPresent = false
            statusMessage = "No hardware step sensor on this device"
            notifyState()
            return
        }

        sensorManager?.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_UI)
        registerActivityTransitions(registeredContext!!)
        notifyState()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return

        val total = event.values[0].toLong()

        if (lastRawTotal == -1L) {
            lastRawTotal = total
            return
        }

        val delta = (total - lastRawTotal).toInt()
        lastRawTotal = total
        if (delta <= 0) return

        val isDriving = activityRecognitionReady && currentActivityType == DetectedActivity.IN_VEHICLE

        if (isDriving) {
            statusMessage = "Ignored $delta step(s): device is in a vehicle"
        } else {
            sessionSteps += delta
            statusMessage = when {
                !activityRecognitionReady -> "Counted $delta step(s) (awaiting activity classification)"
                currentActivityType == DetectedActivity.RUNNING -> "Counted $delta step(s) while running"
                currentActivityType == DetectedActivity.WALKING -> "Counted $delta step(s) while walking"
                currentActivityType == DetectedActivity.ON_FOOT -> "Counted $delta step(s) (on foot)"
                else -> "Counted $delta step(s) (motion confirmed)"
            }
        }
        notifyState()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    private fun registerActivityTransitions(context: Context) {
        val trackedTypes = listOf(
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_FOOT,
            DetectedActivity.STILL,
            DetectedActivity.IN_VEHICLE
        )

        val transitions = trackedTypes.flatMap { type ->
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(type)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(type)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build()
            )
        }

        val request = ActivityTransitionRequest(transitions)
        val intent = Intent(TRANSITIONS_ACTION).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pi = PendingIntent.getBroadcast(context, TRANSITIONS_REQUEST_CODE, intent, flags)
        pendingIntent = pi

        val transitionReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, receivedIntent: Intent?) {
                if (receivedIntent == null || !ActivityTransitionResult.hasResult(receivedIntent)) return
                val result = ActivityTransitionResult.extractResult(receivedIntent) ?: return

                for (event in result.transitionEvents) {
                    when (event.transitionType) {
                        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                            currentActivityType = event.activityType
                            activityRecognitionReady = true
                        }
                        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                            if (event.activityType == currentActivityType) {
                                currentActivityType = DetectedActivity.UNKNOWN
                            }
                        }
                    }
                }
                notifyState()
            }
        }
        receiver = transitionReceiver

        val filter = IntentFilter(TRANSITIONS_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(transitionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(transitionReceiver, filter)
        }

        activityRecognitionClient = ActivityRecognition.getClient(context)
        try {
            activityRecognitionClient
                ?.requestActivityTransitionUpdates(request, pi)
                ?.addOnSuccessListener {
                    activityRecognitionAvailable = true
                    notifyState()
                }
                ?.addOnFailureListener {
                    activityRecognitionAvailable = false
                    statusMessage = "Activity Recognition unavailable — using raw step counter"
                    notifyState()
                }
        } catch (e: SecurityException) {
            activityRecognitionAvailable = false
            statusMessage = "Missing ACTIVITY_RECOGNITION permission"
            notifyState()
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        registeredContext?.let { ctx ->
            pendingIntent?.let { pi ->
                try {
                    activityRecognitionClient?.removeActivityTransitionUpdates(pi)
                        ?.addOnFailureListener { /* Handle removal failure if needed */ }
                } catch (e: SecurityException) {
                    // Permission was revoked or is missing — safe to ignore or log
                }
            }
            receiver?.let {
                try {
                    ctx.unregisterReceiver(it)
                } catch (e: IllegalArgumentException) {
                    // Already unregistered — safe to ignore
                }
            }
        }
        receiver = null
        pendingIntent = null
    }

    fun resetSession() {
        lastRawTotal = -1L
        sessionSteps = 0
        statusMessage = "Session reset"
        notifyState()
    }

    private fun notifyState() {
        onUpdate?.invoke(
            StepEngineState(
                sessionSteps = sessionSteps,
                statusMessage = statusMessage,
                activityLabel = activityLabelFor(currentActivityType, activityRecognitionReady),
                sensorPresent = sensorPresent,
                activityRecognitionAvailable = activityRecognitionAvailable
            )
        )
    }

    private fun activityLabelFor(type: Int, ready: Boolean): String {
        if (!ready) return "Classifying..."
        return when (type) {
            DetectedActivity.WALKING -> "Walking"
            DetectedActivity.RUNNING -> "Running"
            DetectedActivity.ON_FOOT -> "On Foot"
            DetectedActivity.STILL -> "Still"
            DetectedActivity.IN_VEHICLE -> "In Vehicle"
            else -> "Unclassified"
        }
    }
}



object StepEngineManager {
    private val engine = HybridStepEngine()

    private val _state = MutableStateFlow(StepEngineState())
    val state: StateFlow<StepEngineState> = _state.asStateFlow()

    val currentState: StepEngineState
        get() = _state.value

    fun start(context: Context) {
        engine.start(context) { newState ->
            _state.value = newState
        }
    }

    fun resetSession() = engine.resetSession()
    fun stop() = engine.stop()
}