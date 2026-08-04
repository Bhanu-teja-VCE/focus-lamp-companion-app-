package com.focuslamp.app.data.repository

import com.focuslamp.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing Parent-Child family pairing, schedules, and extension requests.
 */
class FamilyRepository {

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(
        listOf(
            PairedDevice(
                deviceId = "DEV_CHILD_01",
                childName = "Alex (Hostel Room 204)",
                deviceModel = "Pixel 6a",
                isOnline = true,
                currentLampState = LampState.GREEN,
                todayScreenTimeMinutes = 42,
                dailyLimitMinutes = 60,
                isSoftRestriction = true
            ),
            PairedDevice(
                deviceId = "DEV_CHILD_02",
                childName = "Sam (Study Desk)",
                deviceModel = "Galaxy Tab A8",
                isOnline = true,
                currentLampState = LampState.WHITE,
                todayScreenTimeMinutes = 55,
                dailyLimitMinutes = 60,
                isSoftRestriction = true
            )
        )
    )
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    private val _extensionRequests = MutableStateFlow<List<ExtensionRequest>>(
        listOf(
            ExtensionRequest(
                id = "REQ_101",
                childId = "DEV_CHILD_02",
                childName = "Sam",
                requestedMinutes = 15,
                reason = "Finishing group study assignment PDF"
            )
        )
    )
    val extensionRequests: StateFlow<List<ExtensionRequest>> = _extensionRequests.asStateFlow()

    private val _scheduleWindows = MutableStateFlow<List<ScheduleWindow>>(
        listOf(
            ScheduleWindow("SCH_01", "School Hours", "09:00", "16:00", listOf(1, 2, 3, 4, 5)),
            ScheduleWindow("SCH_02", "Bedtime Mode", "22:00", "06:00", listOf(1, 2, 3, 4, 5, 6, 7)),
            ScheduleWindow("SCH_03", "Evening Study Focus", "18:00", "20:00", listOf(1, 2, 3, 4, 5))
        )
    )
    val scheduleWindows: StateFlow<List<ScheduleWindow>> = _scheduleWindows.asStateFlow()

    fun approveExtension(requestId: String) {
        _extensionRequests.value = _extensionRequests.value.map { req ->
            if (req.id == requestId) req.copy(isApproved = true) else req
        }
    }

    fun rejectExtension(requestId: String) {
        _extensionRequests.value = _extensionRequests.value.map { req ->
            if (req.id == requestId) req.copy(isApproved = false) else req
        }
    }

    fun addPairedDevice(device: PairedDevice) {
        _pairedDevices.value = _pairedDevices.value + device
    }
}
