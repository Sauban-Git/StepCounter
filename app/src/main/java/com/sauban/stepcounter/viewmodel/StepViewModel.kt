package com.sauban.stepcounter.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sauban.stepcounter.StepEngineManager
import com.sauban.stepcounter.data.StepDatabase
import com.sauban.stepcounter.data.StepRepository
import com.sauban.stepcounter.data.StepSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class StepViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StepRepository

    var uiState by mutableStateOf(StepEngineManager.currentState)
        private set

    var currentSessionStartTime by mutableLongStateOf(System.currentTimeMillis())
        private set

    val historySessions: StateFlow<List<StepSession>>

    init {
        val stepDao = StepDatabase.getDatabase(application).stepDao()
        repository = StepRepository(stepDao)

        historySessions = repository.allSessions.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )

        viewModelScope.launch {
            StepEngineManager.state.collect { newState ->
                uiState = newState
                checkAutoReset()
            }
        }
    }

    fun resetSession() {
        val currentSteps = StepEngineManager.currentState.sessionSteps
        val now = System.currentTimeMillis()

        if (currentSteps > 0) {
            val session = StepSession(
                startTime = currentSessionStartTime,
                endTime = now,
                steps = currentSteps
            )

            viewModelScope.launch(Dispatchers.IO) {
                withContext(NonCancellable) {
                    repository.insertSession(session)
                }
            }
        }

        StepEngineManager.resetSession()
        currentSessionStartTime = now
    }

    fun deleteSession(session: StepSession) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSession(session)
        }
    }

    private fun checkAutoReset() {
        val now = Calendar.getInstance()
        val sessionStart = Calendar.getInstance().apply {
            timeInMillis = currentSessionStartTime
        }

        val isDifferentDay = now.get(Calendar.DAY_OF_YEAR) != sessionStart.get(Calendar.DAY_OF_YEAR) ||
                now.get(Calendar.YEAR) != sessionStart.get(Calendar.YEAR)

        if (isDifferentDay) {
            resetSession()
        }
    }
}