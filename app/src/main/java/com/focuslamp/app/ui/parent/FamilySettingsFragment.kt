package com.focuslamp.app.ui.parent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.focuslamp.app.R
import com.focuslamp.app.utils.ParentPinManager
import com.focuslamp.app.utils.SettingsManager

/**
 * FamilySettingsFragment — Dedicated Parent Mode screen for resetting Parent PIN,
 * viewing ESP32 lamp pairing metadata, and managing emergency whitelisted applications.
 */
class FamilySettingsFragment : Fragment() {

    private lateinit var pinManager: ParentPinManager
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_family_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pinManager = ParentPinManager(requireContext())
        settingsManager = SettingsManager(requireContext())

        val tvPinHint = view.findViewById<TextView>(R.id.tvFamilyPinHint)
        val etPinInput = view.findViewById<EditText>(R.id.etFamilyPinInput)
        val btnClearPin = view.findViewById<Button>(R.id.btnFamilyClearPin)
        val tvLampIp = view.findViewById<TextView>(R.id.tvLampPairingIp)

        tvLampIp.text = "Active ESP32 IP: ${settingsManager.espIp} (mDNS: focuslamp.local)"

        if (pinManager.isPinSet()) {
            tvPinHint.text = "🔒 Parent PIN is ACTIVE. Enter current PIN to clear or change."
            btnClearPin.text = "Clear PIN"
        } else {
            tvPinHint.text = "No Parent PIN configured."
            btnClearPin.text = "Set PIN"
        }

        btnClearPin.setOnClickListener {
            val entered = etPinInput.text.toString().trim()
            if (entered.length != 4) {
                etPinInput.error = "Enter 4-digit PIN"
                return@setOnClickListener
            }

            if (pinManager.isPinSet()) {
                if (pinManager.clearPin(entered)) {
                    etPinInput.setText("")
                    tvPinHint.text = "Parent PIN cleared."
                    btnClearPin.text = "Set PIN"
                    Toast.makeText(requireContext(), "Parent PIN cleared successfully.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "❌ Incorrect PIN!", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (pinManager.setPin(entered)) {
                    etPinInput.setText("")
                    tvPinHint.text = "🔒 Parent PIN is ACTIVE."
                    btnClearPin.text = "Clear PIN"
                    Toast.makeText(requireContext(), "🔒 Parent PIN set successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
