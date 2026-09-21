package com.sauban.stepcounter.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

// 1. Made the constructor private
class SettingsRepository private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

    private val _settingsState = MutableStateFlow(loadSettings())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    private fun loadSettings(): SettingsUiState {
        return SettingsUiState(
            dailyStepGoal = prefs.getInt(KEY_DAILY_GOAL, 10000),
            notificationEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        )
    }

    fun updateDailyStepGoal(goal: Int) {
        prefs.edit { putInt(KEY_DAILY_GOAL, goal) }
        _settingsState.value = _settingsState.value.copy(dailyStepGoal = goal)
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFICATIONS, enabled) }
        _settingsState.value = _settingsState.value.copy(notificationEnabled = enabled)
    }

    companion object {
        private const val KEY_DAILY_GOAL = "daily_step_goal"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"

        // 2. Added Singleton instance manager
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}