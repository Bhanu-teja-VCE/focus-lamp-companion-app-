package com.focuslamp.app.ui.coach

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.data.ai.GroqApiClient
import com.focuslamp.app.databinding.SheetCoachBinding
import com.focuslamp.app.ui.common.repeatWhileStarted
import com.focuslamp.app.ui.common.visible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The AI coach, one tap away from every screen (it used to live inside Settings in a separate
 * activity). Knows which screen it was opened from and sees the same numbers as the app.
 */
class CoachSheet : BottomSheetDialogFragment() {

    private var _b: SheetCoachBinding? = null
    private val b get() = _b!!
    private val adapter = ChatAdapter()
    private val sending = MutableStateFlow(false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        d.behavior.skipCollapsed = true
        d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        return d
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = SheetCoachBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onStart() {
        super.onStart()
        // Full height so the conversation has room.
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            it.requestLayout()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val screen = arguments?.getString(ARG_SCREEN)
        b.messages.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        b.messages.adapter = adapter
        b.close.setOnClickListener { dismiss() }
        b.clear.setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { Graph.chat.clear() } }
        b.send.setOnClickListener { send(b.input.text.toString(), screen) }
        b.input.doAfterTextChanged { updateSend() }
        b.saveKey.setOnClickListener { saveKey() }

        suggestions().forEach { text ->
            b.suggestions.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                this.text = text
                setChipBackgroundColorResource(R.color.surface_raised)
                setTextColor(requireContext().getColor(R.color.paper))
                chipStrokeWidth = 0f
                setOnClickListener { send(text, screen) }
            })
        }

        renderKeyState()
        repeatWhileStarted {
            combine(Graph.chat.messages(), sending) { m, s -> m to s }.collect { (list, isSending) ->
                adapter.submitList(list) {
                    if (list.isNotEmpty()) _b?.messages?.scrollToPosition(list.size - 1)
                }
                b.intro.visible(list.isEmpty())
                b.clear.visible(list.isNotEmpty())
                b.typing.visible(isSending)
                updateSend()
            }
        }
    }

    /** Suggestions built from today's real numbers, so they're worth tapping. */
    private fun suggestions(): List<String> {
        val s = Graph.usage.snapshot.value
        val top = s.apps.firstOrNull { it.distracting }?.label
        return buildList {
            if (top != null) add("How can I spend less time on $top?")
            add("Why is my lamp the color it is?")
            add("Plan a 2-hour study block for tonight")
            add("Is my daily budget realistic?")
        }
    }

    private fun send(raw: String, screen: String?) {
        val text = raw.trim()
        if (text.isEmpty() || sending.value) return
        if (!Graph.settings.isAiEnabled) {
            renderKeyState()
            b.error.visible(true)
            b.error.text = getString(R.string.coach_needs_key)
            return
        }
        b.input.setText("")
        b.error.visible(false)
        sending.value = true
        viewLifecycleOwner.lifecycleScope.launch {
            val r = Graph.chat.send(text, screen)
            sending.value = false
            _b?.let { v ->
                r.onFailure {
                    v.error.visible(true)
                    v.error.text = it.message ?: "The coach couldn't answer. Try again."
                }
            }
        }
    }

    private fun renderKeyState() {
        b.keySetup.visible(!Graph.settings.isAiEnabled)
        b.suggestions.visible(Graph.settings.isAiEnabled)
    }

    private fun updateSend() {
        val v = _b ?: return
        val enabled = v.input.text.isNotBlank() && !sending.value
        v.send.isEnabled = enabled
        v.send.alpha = if (enabled) 1f else 0.4f
    }

    private fun saveKey() {
        val key = b.keyInput.text?.toString()?.trim().orEmpty()
        if (key.isEmpty()) {
            b.keyLayout.error = "Paste your Groq API key."
            return
        }
        b.saveKey.isEnabled = false
        b.keyLayout.helperText = "Checking…"
        viewLifecycleOwner.lifecycleScope.launch {
            val r = GroqApiClient().validate(key)
            val v = _b ?: return@launch
            v.saveKey.isEnabled = true
            v.keyLayout.helperText = null
            r.onSuccess {
                Graph.settings.groqApiKey = key
                v.keyLayout.error = null
                v.error.visible(false)
                renderKeyState()
            }.onFailure { v.keyLayout.error = it.message }
        }
    }

    override fun onDestroyView() {
        b.messages.adapter = null
        super.onDestroyView()
        _b = null
    }

    companion object {
        const val TAG = "coach"
        private const val ARG_SCREEN = "screen"
        fun newInstance(screen: String?) = CoachSheet().apply { arguments = bundleOf(ARG_SCREEN to screen) }
    }
}
