package com.sauban.stepcounter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sauban.stepcounter.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class StepCounterService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val channelId = "step_counter_channel"
    private val notificationId = 1
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()

        settingsRepository = SettingsRepository(applicationContext)
        createNotificationChannel()
        startForegroundNotification()

        StepEngineManager.start(this)

        serviceScope.launch {
            combine(
                StepEngineManager.state,
                settingsRepository.settingsState
            ) {
                engineState, settings ->
                Pair(engineState, settings)
            }.collect { (engineState, settings) ->
                val statusPrefix = if (engineState.isPaused) "[PAUSED]" else ""
                val content = if (settings.notificationEnabled) {
                    "${statusPrefix}Steps: ${engineState.sessionSteps} / ${settings.dailyStepGoal} • ${engineState.activityLabel}"
                } else {
                    "${statusPrefix}Step counter running in background"
                }
                updateNotification(content)
            }

        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Background Step Counter",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Step Counter Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startForegroundNotification() {
        val notification = buildNotification("Initializing steps...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(1, notification)
        }
    }

    private fun updateNotification(content: String) {
        val notification = buildNotification(content)

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(notificationId, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        StepEngineManager.stop(this)
    }
}