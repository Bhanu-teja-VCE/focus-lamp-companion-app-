package com.focuslamp.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.focuslamp.app.R
import com.focuslamp.app.data.tracking.AppUsageItem

class AppUsageAdapter(
    private var items: List<AppUsageItem> = emptyList()
) : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    fun submitList(newItems: List<AppUsageItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        private val tvAppDuration: TextView = itemView.findViewById(R.id.tvAppDuration)

        fun bind(item: AppUsageItem) {
            ivAppIcon.setImageDrawable(item.icon)
            tvAppName.text = item.appName
            
            val minutes = item.usageMillis / 1000 / 60
            val hours = minutes / 60
            val remMins = minutes % 60
            
            if (hours > 0) {
                tvAppDuration.text = "${hours} hr, ${remMins} min"
            } else {
                tvAppDuration.text = "${minutes} minutes"
            }
        }
    }
}
