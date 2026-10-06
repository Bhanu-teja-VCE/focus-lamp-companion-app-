package com.focuslamp.app.ui.lamp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.data.lamp.LampDiscovery
import com.focuslamp.app.data.lamp.LampLink
import com.focuslamp.app.data.lamp.LampState
import com.focuslamp.app.databinding.FragmentLampBinding
import com.focuslamp.app.ui.common.repeatWhileStarted
import com.focuslamp.app.ui.common.setLampDot
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** The lamp: connection, automatic discovery on Wi-Fi, what each color means, and a color test. */
class LampFragment : Fragment() {

    private var _b: FragmentLampBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentLampBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.ipInput.setText(Graph.lamp.host)
        b.greenRow.setLampDot(R.color.lamp_green)
        b.warmRow.setLampDot(R.color.lamp_warm)
        b.redRow.setLampDot(R.color.lamp_red)

        b.connect.setOnClickListener { connect() }
        b.ipInput.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { connect(); true } else false
        }
        b.find.setOnClickListener { find() }
        b.testGreen.setOnClickListener { test(LampState.FOCUS) }
        b.testWarm.setOnClickListener { test(LampState.WARNING) }
        b.testRed.setOnClickListener { test(LampState.DISTRACTION) }

        repeatWhileStarted {
            Graph.lamp.status.collect { s ->
                val ctx = requireContext()
                val state = s.state
                b.glow.setLampColor(ctx.getColor(if (s.link == LampLink.ONLINE && state != null) state.colorRes else R.color.lamp_off))
                b.stateName.text = when (s.link) {
                    LampLink.ONLINE -> state?.label ?: "Connected"
                    LampLink.CONNECTING -> "Connecting…"
                    LampLink.OFFLINE -> "Not reachable"
                    LampLink.UNKNOWN -> "Not checked yet"
                }
                b.linkText.text = when (s.link) {
                    LampLink.ONLINE -> "Connected at ${s.host}"
                    LampLink.OFFLINE -> "Make sure the lamp is on and this phone is on the same Wi-Fi."
                    else -> s.host
                }
            }
        }
    }

    private fun connect() {
        val ip = b.ipInput.text?.toString()?.trim().orEmpty()
        if (ip.isEmpty()) {
            b.ipLayout.error = "Enter the lamp's address, e.g. 192.168.1.120"
            return
        }
        b.ipLayout.error = null
        Graph.lamp.setHost(ip)
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = Graph.lamp.check()
            _b?.let { Snackbar.make(it.root, if (ok) "Lamp connected." else "Couldn't reach the lamp at $ip.", Snackbar.LENGTH_SHORT).show() }
        }
    }

    private fun find() {
        b.find.isEnabled = false
        b.find.text = getString(R.string.lamp_searching)
        viewLifecycleOwner.lifecycleScope.launch {
            val found = LampDiscovery(requireContext()).find()
            val v = _b ?: return@launch
            v.find.isEnabled = true
            v.find.text = getString(R.string.lamp_find)
            if (found != null) {
                v.ipInput.setText(found)
                connect()
            } else {
                Snackbar.make(v.root, "No lamp found on this Wi-Fi. Enter the address shown in the lamp's serial monitor.", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun test(state: LampState) {
        viewLifecycleOwner.lifecycleScope.launch {
            Graph.lamp.apply(state, force = true)
            _b?.let {
                Snackbar.make(it.root, "Sent ${state.label.lowercase()}. The lamp returns to its real color within a few seconds.", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
