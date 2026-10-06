package com.focuslamp.app.ui.common

import android.content.ActivityNotFoundException
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.focuslamp.app.databinding.ItemPermissionBinding
import com.focuslamp.app.util.PermissionItem
import com.focuslamp.app.util.Permissions

/** Renders permission rows with a "Turn on" button that opens the right system screen. */
object PermissionRows {

    fun render(fragment: Fragment, container: ViewGroup, items: List<PermissionItem>, showGranted: Boolean) {
        val ctx = fragment.requireContext()
        container.removeAllViews()
        for (item in items) {
            val granted = Permissions.isGranted(ctx, item)
            if (granted && !showGranted) continue
            val row = ItemPermissionBinding.inflate(LayoutInflater.from(ctx), container, false)
            row.permTitle.text = item.title
            row.permWhy.text = item.why
            row.permAction.visible(!granted)
            row.permDone.visible(granted)
            row.permAction.setOnClickListener {
                val intent = Permissions.settingsIntent(ctx, item) ?: return@setOnClickListener
                try {
                    fragment.startActivity(intent)
                    if (item == PermissionItem.BLOCKER) {
                        Toast.makeText(ctx, "Find Focus Lamp in the list and turn it on.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(ctx, "This phone doesn't have that settings screen.", Toast.LENGTH_SHORT).show()
                }
            }
            container.addView(row.root)
        }
    }
}
