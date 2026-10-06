package com.focuslamp.app.ui.family

import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.focuslamp.app.Graph
import com.focuslamp.app.data.rules.ParentPinManager
import com.focuslamp.app.databinding.DialogPinBinding
import com.focuslamp.app.ui.common.visible
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Parent PIN prompts used on the student's phone and in the rules editor. */
object PinDialog {

    /** Asks for the existing parent PIN. Calls [onOk] only when it's right. */
    fun verify(fragment: Fragment, message: String, onOk: () -> Unit) {
        val pin = Graph.pin
        val b = DialogPinBinding.inflate(LayoutInflater.from(fragment.requireContext()))
        b.pinMessage.text = message
        b.confirmLayout.visible(false)
        val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Parent PIN")
            .setView(b.root)
            .setPositiveButton("Continue", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val wait = pin.lockoutSecondsRemaining()
                when {
                    wait > 0 -> b.pinLayout.error = "Too many tries. Wait $wait seconds."
                    pin.verify(b.pinInput.text?.toString().orEmpty()) -> {
                        dialog.dismiss(); onOk()
                    }
                    else -> b.pinLayout.error = "That PIN isn't right."
                }
            }
        }
        dialog.show()
    }

    /**
     * Asks for a new PIN twice. [onSet] receives the PIN; the caller decides whether to store it
     * on this phone or send its hash to the student's phone.
     */
    fun create(fragment: Fragment, message: String, onSet: (String) -> Unit) {
        val b = DialogPinBinding.inflate(LayoutInflater.from(fragment.requireContext()))
        b.pinMessage.text = message
        val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Choose a parent PIN")
            .setView(b.root)
            .setPositiveButton("Save PIN", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p = b.pinInput.text?.toString().orEmpty()
                val c = b.confirmInput.text?.toString().orEmpty()
                b.pinLayout.error = null
                b.confirmLayout.error = null
                when {
                    !ParentPinManager.isValidPin(p) -> b.pinLayout.error = "Use 4 to 6 digits."
                    p != c -> b.confirmLayout.error = "The two PINs don't match."
                    else -> {
                        dialog.dismiss(); onSet(p)
                    }
                }
            }
        }
        dialog.show()
    }
}
