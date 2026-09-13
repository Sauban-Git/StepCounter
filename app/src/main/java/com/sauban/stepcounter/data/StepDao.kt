package com.sauban.stepcounter.data


import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Insert
    suspend fun insertSession(session: StepSession)

    @Delete
    suspend fun deleteSession(session: StepSession)

    @Query("SELECT * FROM step_sessions ORDER BY endTime DESC")
    fun getAllSessions(): Flow<List<StepSession>>
}