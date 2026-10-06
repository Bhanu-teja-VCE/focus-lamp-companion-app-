package com.focuslamp.app.ui.usage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.focuslamp.app.R
import com.focuslamp.app.databinding.ItemAppUsageBinding
import com.focuslamp.app.ui.common.setAppIcon
import com.focuslamp.app.ui.common.visible
import com.focuslamp.app.util.Format

data class AppUsageRow(
    val packageName: String,
    val label: String,
    val ms: Long,
    val distracting: Boolean,
    val detail: String?,
    val paused: Boolean,
    /** Share of the most-used app, 0..1000, for the thin bar. */
    val share: Int
)

/**
 * The per-app list. A real RecyclerView with DiffUtil: ticking a checkbox updates that one row
 * instead of rebuilding every row and jumping back to the top like the old list did.
 */
class AppUsageAdapter(
    private val onToggle: (AppUsageRow, Boolean) -> Unit,
    private val onOpen: (AppUsageRow) -> Unit
) : ListAdapter<AppUsageRow, AppUsageAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemAppUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val b: ItemAppUsageBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: AppUsageRow) {
            val ctx = b.root.context
            b.icon.setAppIcon(row.packageName, row.label)
            b.name.text = row.label
            b.time.text = if (row.ms == 0L) "0m" else Format.duration(row.ms)
            b.detail.text = row.detail
            b.detail.visible(row.detail != null)
            b.detail.setTextColor(ctx.getColor(if (row.paused) R.color.lamp_red else R.color.text_faint))
            b.share.setIndicatorColor(ctx.getColor(if (row.distracting) R.color.lamp_red else R.color.text_faint))
            b.share.setProgressCompat(row.share, false)

            // Detach the listener first: recycled rows must not fire the previous app's toggle.
            b.distracting.setOnCheckedChangeListener(null)
            b.distracting.isChecked = row.distracting
            b.distracting.contentDescription = ctx.getString(R.string.mark_distracting, row.label)
            b.distracting.setOnCheckedChangeListener { _, checked -> onToggle(row, checked) }
            b.root.setOnClickListener { onOpen(row) }
            b.root.contentDescription = "${row.label}, ${Format.durationWords(row.ms)}${if (row.distracting) ", distracting" else ""}"
        }
    }

    private object Diff : DiffUtil.ItemCallback<AppUsageRow>() {
        override fun areItemsTheSame(a: AppUsageRow, b: AppUsageRow) = a.packageName == b.packageName
        override fun areContentsTheSame(a: AppUsageRow, b: AppUsageRow) = a == b
    }
}
