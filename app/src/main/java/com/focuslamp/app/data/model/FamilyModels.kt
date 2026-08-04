package com.focuslamp.app.data.model

/**
 * Data models for Parent-Child Mode & Family Hub.
 */

enum class FamilyRole {
    PARENT, CHILD, CO_PARENT, GUARDIAN
}

data class FamilyUser(
    val id: String,
    val name: String,
    val email: String,
    val role: FamilyRole,
    val pairedDeviceId: String? = null
)

data class PairedDevice(
    val deviceId: String,
    val childName: String,
    val deviceModel: String,
    val isOnline: Boolean = true,
    val currentLampState: LampState = LampState.GREEN,
    val todayScreenTimeMinutes: Long = 0,
    val dailyLimitMinutes: Long = 60,
    val isSoftRestriction: Boolean = true,
    val pairedAt: Long = System.currentTimeMillis()
)

data class ScheduleWindow(
    val id: String,
    val title: String, // e.g. "School Hours", "Bedtime Mode", "Focus Hours"
    val startTime: String, // e.g. "09:00"
    val endTime: String,   // e.g. "17:00"
    val daysOfWeek: List<Int>, // 1 = Mon ... 7 = Sun
    val isWeekendRule: Boolean = false,
    val allowedAppCategory: String = "Educational"
)

data class ExtensionRequest(
    val id: String,
    val childId: String,
    val childName: String,
    val requestedMinutes: Int,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isApproved: Boolean? = null // null = pending, true = approved, false = rejected
)

data class FamilyAgreement(
    val familyName: String,
    val rules: List<String>,
    val signedByParent: Boolean = true,
    val signedByChild: Boolean = true,
    val emergencyApps: List<String> = listOf("Phone", "Messages", "Maps", "Medical")
)
