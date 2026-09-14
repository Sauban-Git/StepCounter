package com.sauban.stepcounter.data

import kotlinx.coroutines.flow.Flow

class StepRepository(private val stepDao: StepDao) {
    val allSessions: Flow<List<StepSession>> = stepDao.getAllSessions()

    suspend fun insertSession(session: StepSession) {
        stepDao.insertSession(session)
    }

    suspend fun deleteSession(session: StepSession) {
        stepDao.deleteSession(session)
    }
}

