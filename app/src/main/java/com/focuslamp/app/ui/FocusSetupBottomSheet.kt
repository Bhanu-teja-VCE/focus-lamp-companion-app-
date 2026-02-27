package com.focuslamp.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import androidx.lifecycle.ViewModelProvider
import com.focuslamp.app.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom Sheet dialog for setting up a focus session duration before starting.
 */
class FocusSetupBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: FocusViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_setup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[FocusViewModel::class.java]

        val numberPicker = view.findViewById<NumberPicker>(R.id.numberPickerDuration)
        val btnStart = view.findViewById<Button>(R.id.btnStartSession)

        // Configure number picker: 5 to 120 minutes, step 5
        numberPicker.minValue = 1
        numberPicker.maxValue = 24
        numberPicker.value = 5 // Default = 25 min (index 5 → 25)
        numberPicker.displayedValues = (1..24).map { "${it * 5} min" }.toTypedArray()
        numberPicker.wrapSelectorWheel = false

        btnStart.setOnClickListener {
            val durationMinutes = (numberPicker.value) * 5
            viewModel.selectedDurationMinutes = durationMinutes
            viewModel.startFocusSession()
            dismiss()
        }
    }
}
