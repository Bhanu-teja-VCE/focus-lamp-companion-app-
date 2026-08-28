package com.focuslamp.app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.focuslamp.app.R
import com.focuslamp.app.data.local.ChatMessageEntity

/**
 * ChatAdapter — RecyclerView adapter displaying user and AI message bubbles.
 */
class ChatAdapter : ListAdapter<ChatMessageEntity, RecyclerView.ViewHolder>(DiffCallback) {

    companion me {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2

        private object DiffCallback : DiffUtil.ItemCallback<ChatMessageEntity>() {
            override fun areItemsTheSame(oldItem: ChatMessageEntity, newItem: ChatMessageEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ChatMessageEntity, newItem: ChatMessageEntity): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).sender == "user") VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_USER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_ai, parent, false)
            AiViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is UserViewHolder) {
            holder.bind(item)
        } else if (holder is AiViewHolder) {
            holder.bind(item)
        }
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvUserMessage)
        fun bind(item: ChatMessageEntity) {
            tvMessage.text = item.text
        }
    }

    class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvAiMessage)
        fun bind(item: ChatMessageEntity) {
            tvMessage.text = item.text
        }
    }
}
