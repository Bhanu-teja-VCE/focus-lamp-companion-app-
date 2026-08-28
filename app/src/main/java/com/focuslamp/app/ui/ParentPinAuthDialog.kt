package com.focuslamp.app.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.focuslamp.app.R
import com.focuslamp.app.utils.ParentPinManager

/**
 * ParentPinAuthDialog — Secure PIN prompt dialog launched before entering Parent Mode.
 * Enforces exponential backoff countdowns on failed attempts.
 */
class ParentPinAuthDialog(
    context: Context,
    private val onSuccess: () -> Unit
) : Dialog(context) {

    private val pinManager = ParentPinManager(context)
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_parent_pin_auth)

        val tvTitle = findViewById<TextView>(R.id.tvDialogTitle)
        val tvSubTitle = findViewById<TextView>(R.id.tvDialogSubtitle)
        val etPin = findViewById<EditText>(R.id.etPinInput)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitPin)
        val btnCancel = findViewById<Button>(R.id.btnCancelPin)
        val tvLockoutWarning = findViewById<TextView>(R.id.tvLockoutWarning)

        val isPinSet = pinManager.isPinSet()
        if (isPinSet) {
            tvTitle.text = "🔒 Enter Parent PIN"
            tvSubTitle.text = "Parent authentication required to enter Parent Mode."
            btnSubmit.text = "Unlock Parent Mode"
        } else {
            tvTitle.text = "🔒 Set Parent PIN"
            tvSubTitle.text = "Set a 4-digit Parent PIN to lock parent controls."
            btnSubmit.text = "Set PIN & Continue"
        }

        // Check if currently locked out
        checkLockout(tvLockoutWarning, etPin, btnSubmit)

        btnSubmit.setOnClickListener {
            val enteredPin = etPin.text.toString().trim()

            if (enteredPin.length != 4) {
                etPin.error = "PIN must be 4 digits"
                return@setOnClickListener
            }

            if (!isPinSet) {
                // Set new PIN
                if (pinManager.setPin(enteredPin)) {
                    Toast.makeText(context, "🔒 Parent PIN created successfully!", Toast.LENGTH_SHORT).show()
                    dismiss()
                    onSuccess()
                }
            } else {
                // Verify PIN
                if (pinManager.verifyPin(enteredPin)) {
                    dismiss()
                    onSuccess()
                } else {
                    etPin.setText("")
                    val lockoutSecs = pinManager.getLockoutSecondsRemaining()
                    if (lockoutSecs > 0) {
                        checkLockout(tvLockoutWarning, etPin, btnSubmit)
                    } else {
                        Toast.makeText(context, "❌ Incorrect Parent PIN!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun checkLockout(tvWarning: TextView, etInput: EditText, btnSubmit: Button) {
        val remainingSecs = pinManager.getLockoutSecondsRemaining()
        if (remainingSecs > 0) {
            etInput.isEnabled = false
            btnSubmit.isEnabled = false
            tvWarning.visibility = View.VISIBLE

            countDownTimer?.cancel()
            countDownTimer = object : CountDownTimer(remainingSecs * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val secsLeft = (millisUntilFinished / 1000).toInt() + 1
                    tvWarning.text = "⛔ Too many failed PIN attempts. Locked out for $secsLeft seconds..."
                }

                override fun onFinish() {
                    tvWarning.visibility = View.GONE
                    etInput.isEnabled = true
                    btnSubmit.isEnabled = true
                }
            }.start()
        } else {
            tvWarning.visibility = View.GONE
            etInput.isEnabled = true
            btnSubmit.isEnabled = true
        }
    }

    override fun onStop() {
        super.onStop()
        countDownTimer?.cancel()
    }
}
