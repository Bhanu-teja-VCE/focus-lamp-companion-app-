package com.focuslamp.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single focus session stored in the Room database.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Unix timestamp when the session was created */
    val timestamp: Long,

    /** Duration of the session in minutes */
    val durationMinutes: Int,

    /** Whether the session completed fully or was stopped early */
    val isCompleted: Boolean,

    /** Total distraction time (minutes) recorded during this session */
    val distractionMinutes: Long = 0
)
