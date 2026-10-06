package com.focuslamp.app.ui.coach

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.focuslamp.app.data.local.ChatMessageEntity
import com.focuslamp.app.databinding.ItemChatAiBinding
import com.focuslamp.app.databinding.ItemChatUserBinding

class ChatAdapter : ListAdapter<ChatMessageEntity, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) = if (getItem(position).sender == "user") USER else AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == USER) UserHolder(ItemChatUserBinding.inflate(inf, parent, false))
        else AiHolder(ItemChatAiBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = getItem(position)
        when (holder) {
            is UserHolder -> holder.b.text.text = m.text
            is AiHolder -> holder.b.text.text = MarkdownLite.render(m.text)
        }
    }

    class UserHolder(val b: ItemChatUserBinding) : RecyclerView.ViewHolder(b.root)
    class AiHolder(val b: ItemChatAiBinding) : RecyclerView.ViewHolder(b.root)

    private object Diff : DiffUtil.ItemCallback<ChatMessageEntity>() {
        override fun areItemsTheSame(a: ChatMessageEntity, b: ChatMessageEntity) = a.id == b.id
        override fun areContentsTheSame(a: ChatMessageEntity, b: ChatMessageEntity) = a == b
    }

    companion object {
        private const val USER = 1
        private const val AI = 2
    }
}
