package com.focuslamp.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.focuslamp.app.R
import com.focuslamp.app.data.local.SessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History screen — displays a list of past focus sessions.
 */
class HistoryFragment : Fragment(R.layout.fragment_history) {

    private lateinit var viewModel: FocusViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[FocusViewModel::class.java]

        val tvNoHistory = view.findViewById<TextView>(R.id.tvNoHistory)
        val rvHistory = view.findViewById<RecyclerView>(R.id.rvHistory)

        rvHistory.layoutManager = LinearLayoutManager(requireContext())

        viewModel.sessionHistory.observe(viewLifecycleOwner) { sessions ->
            if (sessions.isEmpty()) {
                tvNoHistory.visibility = View.VISIBLE
                rvHistory.visibility = View.GONE
            } else {
                tvNoHistory.visibility = View.GONE
                rvHistory.visibility = View.VISIBLE
                rvHistory.adapter = SessionHistoryAdapter(sessions)
            }
        }

        viewModel.loadStats()
    }

    /**
     * Simple RecyclerView adapter for session history items.
     */
    inner class SessionHistoryAdapter(
        private val sessions: List<SessionEntity>
    ) : RecyclerView.Adapter<SessionHistoryAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvDate: TextView = itemView.findViewById(R.id.tvSessionDate)
            val tvDuration: TextView = itemView.findViewById(R.id.tvSessionDuration)
            val tvStatus: TextView = itemView.findViewById(R.id.tvSessionStatus)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = sessions[position]
            val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            holder.tvDate.text = dateFormat.format(Date(session.timestamp))
            holder.tvDuration.text = "${session.durationMinutes} min"
            holder.tvStatus.text = if (session.isCompleted) "✅ Completed" else "⏹ Stopped"
        }

        override fun getItemCount() = sessions.size
    }
}
