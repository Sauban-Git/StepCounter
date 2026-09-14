package com.sauban.stepcounter.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sauban.stepcounter.data.SettingsRepository
import com.sauban.stepcounter.data.SettingsUiState
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = repository.settingsState

    fun updateDailyGoal(newGoal: Int) {
        repository.updateDailyStepGoal(newGoal)
    }

    fun toggleNotifications(enabled: Boolean) {
        repository.updateNotificationsEnabled(enabled)
    }
}

class SettingsViewModelFactory(
    private val repository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}