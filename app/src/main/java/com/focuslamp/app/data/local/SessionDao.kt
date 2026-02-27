package com.focuslamp.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for session history.
 */
@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY timestamp DESC")
    suspend fun getAllSessions(): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE isCompleted = 1")
    suspend fun getCompletedSessionCount(): Int

    @Query("SELECT SUM(durationMinutes) FROM sessions WHERE isCompleted = 1")
    suspend fun getTotalFocusMinutes(): Long?

    @Query("DELETE FROM sessions")
    suspend fun clearAll()
}
