package com.sauban.stepcounter.data


import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "step_sessions")
data class StepSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long,
    val steps: Int
)

data class StepEngineState(
    val sessionSteps: Int = 0,
    val statusMessage: String = "",
    val activityLabel: String = "Waiting...",
    val sensorPresent: Boolean = true,
    val activityRecognitionAvailable: Boolean = true,
    val isPaused: Boolean = false
)

data class SettingsUiState(
    val dailyStepGoal: Int =  10000,
    val notificationEnabled: Boolean = true
)