package com.focuslamp.app.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.focuslamp.app.R
import com.focuslamp.app.utils.ParentPinManager

/**
 * ParentPinAuthDialog — Secure Parent PIN authentication prompt dialog.
 * Features Forgot PIN recovery architecture:
 * 1. Biometric / Device Credential Confirmation (Fingerprint / Face / Lock Screen PIN)
 * 2. 8-character Recovery Code Fallback (displayed ONCE at PIN setup)
 * 3. 24-Hour Time-Delay Fallback if code is lost.
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
        val tvForgotPin = findViewById<TextView>(R.id.tvForgotPin)
        val tvLockoutWarning = findViewById<TextView>(R.id.tvLockoutWarning)
        val containerRecoveryCode = findViewById<LinearLayout>(R.id.containerRecoveryCodeDisplay)
        val tvRecoveryCodeText = findViewById<TextView>(R.id.tvRecoveryCodeText)

        // Check if 24-hour time-delay reset has elapsed
        if (pinManager.isDelayResetUnlocked()) {
            pinManager.resetPinWithoutAuth()
            Toast.makeText(context, "🔓 24-hour recovery window elapsed. Parent PIN reset.", Toast.LENGTH_LONG).show()
        }

        var isPinSet = pinManager.isPinSet()
        updateUiState(isPinSet, tvTitle, tvSubTitle, btnSubmit, tvForgotPin)

        // Check if currently locked out
        checkLockout(tvLockoutWarning, etPin, btnSubmit)

        btnSubmit.setOnClickListener {
            val enteredPin = etPin.text.toString().trim()

            if (enteredPin.length != 4) {
                etPin.error = "PIN must be 4 digits"
                return@setOnClickListener
            }

            if (!isPinSet) {
                // Set new PIN & generate Recovery Code
                val recoveryCode = pinManager.setPin(enteredPin)
                if (recoveryCode != null) {
                    tvRecoveryCodeText.text = recoveryCode
                    containerRecoveryCode.visibility = View.VISIBLE
                    Toast.makeText(context, "🔒 Parent PIN created! Save your recovery code below.", Toast.LENGTH_LONG).show()

                    btnSubmit.text = "Continue to App"
                    btnSubmit.setOnClickListener {
                        dismiss()
                        onSuccess()
                    }
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

        // Forgot PIN Handler: Biometrics ➔ Recovery Code ➔ 24h Delay
        tvForgotPin.setOnClickListener {
            handleForgotPinFlow(tvTitle, tvSubTitle, btnSubmit, tvForgotPin)
        }

        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun updateUiState(
        isPinSet: Boolean,
        tvTitle: TextView,
        tvSubTitle: TextView,
        btnSubmit: Button,
        tvForgotPin: TextView
    ) {
        if (isPinSet) {
            tvTitle.text = "🔒 Enter Parent PIN"
            tvSubTitle.text = "Parent authentication required to enter Parent Mode."
            btnSubmit.text = "Unlock Parent Mode"
            tvForgotPin.visibility = View.VISIBLE
        } else {
            tvTitle.text = "🔒 Set Parent PIN"
            tvSubTitle.text = "Set a 4-digit Parent PIN to lock parent controls."
            btnSubmit.text = "Set PIN & Continue"
            tvForgotPin.visibility = View.GONE
        }
    }

    private fun handleForgotPinFlow(
        tvTitle: TextView,
        tvSubTitle: TextView,
        btnSubmit: Button,
        tvForgotPin: TextView
    ) {
        // Step 1: Try Biometric / Device Credential Auth
        if (context is AppCompatActivity) {
            val biometricManager = BiometricManager.from(context)
            val canAuth = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )

            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(
                    context as AppCompatActivity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            pinManager.resetPinWithoutAuth()
                            Toast.makeText(context, "✅ Device Biometrics Verified! Set a new Parent PIN.", Toast.LENGTH_LONG).show()
                            updateUiState(false, tvTitle, tvSubTitle, btnSubmit, tvForgotPin)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            // Biometrics canceled or unavailable ➔ Step 2: Recovery Code
                            promptRecoveryCode(tvTitle, tvSubTitle, btnSubmit, tvForgotPin)
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Parent Mode Recovery")
                    .setSubtitle("Confirm device fingerprint, face, or lock screen PIN")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()

                biometricPrompt.authenticate(promptInfo)
                return
            }
        }

        // Biometrics unavailable ➔ Step 2: Recovery Code
        promptRecoveryCode(tvTitle, tvSubTitle, btnSubmit, tvForgotPin)
    }

    private fun promptRecoveryCode(
        tvTitle: TextView,
        tvSubTitle: TextView,
        btnSubmit: Button,
        tvForgotPin: TextView
    ) {
        val etCode = EditText(context)
        etCode.hint = "e.g. FL-84A2-9B3C"

        AlertDialog.Builder(context)
            .setTitle("🔑 Enter Recovery Code")
            .setMessage("Enter the 8-character recovery code shown when you created your PIN.")
            .setView(etCode)
            .setPositiveButton("Verify Code") { dialog, _ ->
                val code = etCode.text.toString().trim()
                if (pinManager.verifyRecoveryCode(code)) {
                    Toast.makeText(context, "✅ Recovery Code Verified! Set a new Parent PIN.", Toast.LENGTH_LONG).show()
                    updateUiState(false, tvTitle, tvSubTitle, btnSubmit, tvForgotPin)
                } else {
                    Toast.makeText(context, "❌ Invalid Recovery Code!", Toast.LENGTH_SHORT).show()
                    prompt24HourDelayFallback()
                }
            }
            .setNegativeButton("Lost Code?", { _, _ -> prompt24HourDelayFallback() })
            .show()
    }

    private fun prompt24HourDelayFallback() {
        val remainingHours = pinManager.getDelayResetHoursRemaining()
        if (remainingHours > 0) {
            AlertDialog.Builder(context)
                .setTitle("⏳ 24-Hour Reset Pending")
                .setMessage("A 24-hour security reset was requested. Parent PIN reset will unlock in approximately $remainingHours hour(s).")
                .setPositiveButton("OK", null)
                .show()
        } else {
            AlertDialog.Builder(context)
                .setTitle("⏳ 24-Hour Reset Fallback")
                .setMessage("If you lost your recovery code, you can initiate a 24-hour security delay reset. After 24 hours, Parent PIN lock will automatically reset.")
                .setPositiveButton("Start 24h Delay Reset") { _, _ ->
                    pinManager.startDelayReset()
                    Toast.makeText(context, "⏳ 24-hour reset initiated. Check back in 24 hours.", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
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
