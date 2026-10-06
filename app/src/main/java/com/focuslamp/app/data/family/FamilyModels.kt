package com.focuslamp.app.data.family

import com.focuslamp.app.data.rules.Schedule

data class RemoteApp(val packageName: String, val label: String, val ms: Long = 0L, val distracting: Boolean = false)

data class BlockedApp(val packageName: String, val label: String, val byParent: Boolean, val reason: String)

/** What the student's phone reports to the parent. */
data class ChildStatus(
    val uid: String,
    val name: String,
    val device: String,
    val lastSeen: Long,
    val totalMs: Long,
    val distractionMs: Long,
    val budgetMinutes: Int,
    val pickups: Int,
    val protectionOk: Boolean,
    val issues: List<String>,
    val apps: List<RemoteApp>,
    val installed: List<RemoteApp>,
    val blocked: List<BlockedApp>
) {
    fun isStale(now: Long = System.currentTimeMillis()) = now - lastSeen > 30 * 60_000L
}

enum class RequestStatus { PENDING, APPROVED, DENIED }

data class TimeRequest(
    val id: String,
    val childUid: String,
    val childName: String,
    val packageName: String,
    val appLabel: String,
    val minutes: Int,
    val reason: String,
    val status: RequestStatus,
    val createdAt: Long,
    val resolvedAt: Long,
    val grantedMinutes: Int,
    val byParent: Boolean
)

/** Rules a parent sets from their phone. */
data class RemoteRules(
    val parentLimits: Map<String, Int>,
    val schedules: List<Schedule>,
    val pinHash: String?,
    val pinSalt: String?,
    val updatedAt: Long
)
