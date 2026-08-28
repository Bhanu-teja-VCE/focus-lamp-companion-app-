package com.focuslamp.app.ui.parent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.focuslamp.app.R
import com.focuslamp.app.utils.ScheduleManager

/**
 * ScheduleManagerFragment — Dedicated Parent Mode screen for creating and managing
 * School Hours and Bedtime Mode restriction schedules.
 */
class ScheduleManagerFragment : Fragment() {

    private lateinit var scheduleManager: ScheduleManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_schedule_manager, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleManager = ScheduleManager(requireContext())

        val switchSchool = view.findViewById<SwitchCompat>(R.id.switchSchoolSchedule)
        val switchBedtime = view.findViewById<SwitchCompat>(R.id.switchBedtimeSchedule)
        val btnSave = view.findViewById<Button>(R.id.btnSaveScheduleSettings)

        switchSchool.isChecked = scheduleManager.isSchoolModeEnabled
        switchBedtime.isChecked = scheduleManager.isBedtimeModeEnabled

        btnSave.setOnClickListener {
            scheduleManager.isSchoolModeEnabled = switchSchool.isChecked
            scheduleManager.isBedtimeModeEnabled = switchBedtime.isChecked
            Toast.makeText(requireContext(), "✅ Scheduled restriction windows saved!", Toast.LENGTH_SHORT).show()
        }
    }
}
