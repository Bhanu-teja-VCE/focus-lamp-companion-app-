package com.focuslamp.app.ui.block

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.data.family.RequestStatus
import com.focuslamp.app.data.rules.BlockDecision
import com.focuslamp.app.data.rules.BlockReason
import com.focuslamp.app.data.rules.Grant
import com.focuslamp.app.databinding.OverlayBlockBinding
import com.focuslamp.app.util.DayClock
import com.focuslamp.app.util.Format
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The full-screen block page drawn over a blocked app.
 *
 * Self-set limits: explains the limit and when it resets. No way around it until then.
 * Parent limits: lets the student ask the parent's phone for time, or a parent who is
 * physically there can unlock with the PIN.
 */
class BlockOverlay(
    hostContext: Context,
    private val viaAccessibility: Boolean,
    private val onGoHome: () -> Unit
) {
    private val context = ContextThemeWrapper(hostContext, R.style.Theme_FocusLamp)
    private val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val b = OverlayBlockBinding.inflate(LayoutInflater.from(context))
    private val handler = Handler(Looper.getMainLooper())
    private var decision: BlockDecision? = null
    private var pin = ""
    private var watchJob: Job? = null
    private var pendingRequestId: String? = null

    var isShowing = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            decision?.let { renderUntil(it) }
            handler.postDelayed(this, 30_000L)
        }
    }

    init {
        b.homeButton.setOnClickListener { onGoHome() }
        b.parentHereButton.setOnClickListener { showPin(true) }
        listOf(15, 30, 60).forEachIndexed { i, m ->
            b.minutesGroup.addView(chip(Format.minutes(m), m, checked = i == 1))
        }
        listOf("Homework", "Talking to family", "Something else").forEach {
            b.reasonGroup.addView(chip(it, 0, checked = false))
        }
        b.askButton.setOnClickListener { ask() }
        buildKeypad()
    }

    fun show(d: BlockDecision) {
        val samePackage = decision?.packageName == d.packageName
        decision = d
        if (!samePackage) {
            pin = ""
            pendingRequestId = null
            showPin(false)
        }
        render(d)
        if (!isShowing) {
            try {
                wm.addView(b.root, params())
                isShowing = true
                handler.post(tick)
            } catch (e: Exception) {
                isShowing = false
            }
        }
        watchRequests()
    }

    fun hide() {
        handler.removeCallbacks(tick)
        watchJob?.cancel()
        watchJob = null
        if (isShowing) {
            runCatching { wm.removeView(b.root) }
            isShowing = false
        }
        decision = null
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (viaAccessibility) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.OPAQUE
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun render(d: BlockDecision) {
        val label = Graph.appInfo.label(d.packageName)
        val parentName = Graph.settings.parentName.ifBlank { "your parent" }
        b.appIcon.setImageDrawable(Graph.appInfo.icon(d.packageName))
        b.title.text = "$label is paused"
        b.reason.text = when (d.reason) {
            BlockReason.APP_LIMIT -> "You gave yourself ${Format.minutes(d.limitMinutes ?: 0)} a day on $label, and it's used up."
            BlockReason.DAILY_BUDGET -> "Your ${Format.minutes(d.limitMinutes ?: 0)} for distracting apps today is used up."
            BlockReason.FOCUS_SESSION -> "You're in a focus session. Distracting apps are paused until it ends."
            BlockReason.SCHEDULE -> "${d.scheduleName ?: "A schedule"} is on. ${parentName.replaceFirstChar { it.uppercase() }} set this."
            BlockReason.PARENT_LIMIT, BlockReason.PARENT_HOLD ->
                "${parentName.replaceFirstChar { it.uppercase() }} set ${Format.minutes(d.limitMinutes ?: 0)} a day for $label, and it's used up."
        }
        renderUntil(d)

        val byParent = d.reason.setByParent
        val family = Graph.family
        val canAsk = byParent && family.isAvailable && family.isPaired
        b.requestSection.visibility = if (canAsk) View.VISIBLE else View.GONE
        b.askButton.text = "Ask ${Graph.settings.parentName.ifBlank { "parent" }}"
        b.parentHereButton.visibility =
            if (byParent && Graph.pin.isPinSet() && b.pinSection.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        if (byParent && !canAsk && !Graph.pin.isPinSet()) {
            b.requestStatus.visibility = View.GONE
            b.reason.append("\n\nNo parent phone is linked and no parent PIN is set, so this stays closed until a parent changes the limit.")
        }
    }

    private fun renderUntil(d: BlockDecision) {
        val until = d.until
        b.until.text = when {
            until == null -> "Stays closed until a parent opens it"
            d.reason == BlockReason.FOCUS_SESSION -> "Session ends at ${Format.clock(until)}"
            until >= DayClock.nextMidnight() - 60_000L && until <= DayClock.nextMidnight() + 60_000L ->
                "Opens again at midnight, in ${Format.durationWords(until - System.currentTimeMillis())}"
            else -> "Opens again at ${Format.clock(until)}"
        }
    }

    // ---- Asking the parent's phone ----

    private fun ask() {
        val d = decision ?: return
        val minutes = selectedMinutes()
        val reason = b.reasonGroup.checkedChipId.takeIf { it != View.NO_ID }
            ?.let { b.reasonGroup.findViewById<Chip>(it)?.text?.toString() } ?: ""
        b.askButton.isEnabled = false
        setStatus("Sending…")
        Graph.appScope.launch {
            val result = Graph.family.requestTime(d.packageName, Graph.appInfo.label(d.packageName), minutes, reason)
            result.onSuccess { id ->
                pendingRequestId = id
                setStatus("Asked for ${Format.minutes(minutes)}. Waiting for ${Graph.settings.parentName.ifBlank { "your parent" }}…")
            }.onFailure {
                b.askButton.isEnabled = true
                setStatus("Couldn't send the request. Check the internet connection and try again.")
            }
        }
    }

    /** Keeps the status line in sync with the parent's answer. */
    private fun watchRequests() {
        if (watchJob?.isActive == true) return
        watchJob = Graph.appScope.launch {
            Graph.family.myRequests.collectLatest { list ->
                val d = decision ?: return@collectLatest
                val r = list.firstOrNull { it.id == pendingRequestId }
                    ?: list.firstOrNull { it.packageName == d.packageName && it.status == RequestStatus.PENDING }
                    ?: return@collectLatest
                pendingRequestId = r.id
                when (r.status) {
                    RequestStatus.PENDING -> {
                        b.askButton.isEnabled = false
                        setStatus("Asked for ${Format.minutes(r.minutes)}. Waiting for ${Graph.settings.parentName.ifBlank { "your parent" }}…")
                    }
                    RequestStatus.DENIED -> {
                        b.askButton.isEnabled = true
                        setStatus("Not approved this time.")
                    }
                    RequestStatus.APPROVED -> setStatus("Approved. Opening…") // the grant unblocks the app
                }
            }
        }
    }

    private fun setStatus(text: String) {
        b.requestStatus.visibility = View.VISIBLE
        b.requestStatus.text = text
    }

    // ---- Parent PIN ----

    private fun showPin(show: Boolean) {
        b.pinSection.visibility = if (show) View.VISIBLE else View.GONE
        b.parentHereButton.visibility = if (show) View.GONE else b.parentHereButton.visibility
        b.pinError.visibility = View.GONE
        pin = ""
        renderPin()
    }

    private fun buildKeypad() {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        val size = (68 * context.resources.displayMetrics.density).toInt()
        val margin = (6 * context.resources.displayMetrics.density).toInt()
        keys.forEach { k ->
            val btn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = k
                textSize = 22f
                insetTop = 0
                insetBottom = 0
                cornerRadius = size / 2
                setTextColor(context.getColor(R.color.paper))
                strokeWidth = 0
                setBackgroundColor(context.getColor(if (k.isEmpty()) android.R.color.transparent else R.color.surface_raised))
                isEnabled = k.isNotEmpty()
                contentDescription = if (k == "⌫") "Delete" else k
                setOnClickListener { onKey(k) }
            }
            b.keypad.addView(btn, GridLayout.LayoutParams().apply {
                width = size; height = size
                setMargins(margin, margin, margin, margin)
            })
        }
    }

    private fun onKey(k: String) {
        val wait = Graph.pin.lockoutSecondsRemaining()
        if (wait > 0) {
            b.pinError.visibility = View.VISIBLE
            b.pinError.text = "Too many tries. Wait $wait seconds."
            return
        }
        pin = if (k == "⌫") pin.dropLast(1) else (pin + k).take(6)
        b.pinError.visibility = View.GONE
        renderPin()
        if (pin.length >= 4 && Graph.pin.verify(pin)) {
            unlockWithPin()
        } else if (pin.length == 6) {
            pin = ""
            renderPin()
            b.pinError.visibility = View.VISIBLE
            b.pinError.text = "That PIN isn't right."
        }
    }

    private fun renderPin() {
        b.pinDots.text = "●".repeat(pin.length).ifEmpty { " " }
    }

    private fun unlockWithPin() {
        val d = decision ?: return
        val minutes = selectedMinutes()
        Graph.rules.applyGrant(
            Grant(d.packageName, minutes, DayClock.dayKey(), System.currentTimeMillis(), "pin-${System.currentTimeMillis()}")
        )
        // RulesStore notifies the Enforcer, which hides this screen.
    }

    private fun selectedMinutes(): Int =
        b.minutesGroup.checkedChipId.takeIf { it != View.NO_ID }
            ?.let { b.minutesGroup.findViewById<Chip>(it)?.tag as? Int } ?: 30

    private fun chip(text: String, minutes: Int, checked: Boolean) =
        Chip(context, null, com.google.android.material.R.attr.chipStyle).apply {
            setChipBackgroundColorResource(R.color.chip_bg)
            setTextColor(context.getColorStateList(R.color.chip_text))
            chipStrokeWidth = 0f
            isCheckable = true
            isCheckedIconVisible = false
            this.text = text
            tag = minutes
            id = View.generateViewId()
            isChecked = checked
        }
}
